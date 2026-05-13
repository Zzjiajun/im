package com.im.server.service.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageDeliverMapper;
import com.im.server.mapper.MessageReadMapper;
import com.im.server.model.dto.MarkDeliveredRequest;
import com.im.server.model.dto.MarkReadRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.MessageDeliver;
import com.im.server.model.entity.MessageRead;
import com.im.server.model.vo.MessageReceiptVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.WsEvent;
import com.im.server.service.ConversationService;
import com.im.server.service.UserService;
import com.im.server.service.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息回执服务 —— 负责消息的已读/送达回执。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadService {

    private final ChatMessageMapper chatMessageMapper;
    private final MessageReadMapper messageReadMapper;
    private final MessageDeliverMapper messageDeliverMapper;
    private final ConversationService conversationService;
    private final WsPushService wsPushService;
    private final UserService userService;
    private final MessageVOHelper messageVOHelper;

    // ==================== 已读回执 ====================

    public List<MessageReceiptVO> listReadReceipts(Long userId, Long messageId) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, messageId);
        return messageReadMapper.selectList(
            new LambdaQueryWrapper<MessageRead>()
                .eq(MessageRead::getMessageId, message.getId())
                .orderByAsc(MessageRead::getReadAt)
        ).stream().map(read -> {
            UserSimpleVO user = userService.getSimpleUser(read.getUserId());
            return MessageReceiptVO.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .actionAt(read.getReadAt())
                .build();
        }).toList();
    }

    public List<MessageReceiptVO> listDeliverReceipts(Long userId, Long messageId) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, messageId);
        return messageDeliverMapper.selectList(
            new LambdaQueryWrapper<MessageDeliver>()
                .eq(MessageDeliver::getMessageId, message.getId())
                .orderByAsc(MessageDeliver::getDeliveredAt)
        ).stream().map(deliver -> {
            UserSimpleVO user = userService.getSimpleUser(deliver.getUserId());
            return MessageReceiptVO.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .actionAt(deliver.getDeliveredAt())
                .build();
        }).toList();
    }

    @Transactional
    public void markRead(Long userId, MarkReadRequest request) {
        conversationService.assertUserInConversation(userId, request.getConversationId());

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, request.getConversationId())
            .ne(ChatMessage::getSenderId, userId)
            .orderByAsc(ChatMessage::getId)
            .last("limit 500");
        if (request.getLastReadMessageId() != null) {
            wrapper.le(ChatMessage::getId, request.getLastReadMessageId());
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        if (messages.isEmpty()) {
            conversationService.markRead(userId, request.getConversationId());
            return;
        }

        List<Long> msgIds = messages.stream().map(ChatMessage::getId).toList();
        Set<Long> alreadyReadIds = messageReadMapper.selectList(
            new LambdaQueryWrapper<MessageRead>()
                .eq(MessageRead::getUserId, userId)
                .in(MessageRead::getMessageId, msgIds)
        ).stream().map(MessageRead::getMessageId).collect(Collectors.toSet());

        List<Long> readMessageIds = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ChatMessage message : messages) {
            if (alreadyReadIds.contains(message.getId())) continue;
            MessageRead read = new MessageRead();
            read.setMessageId(message.getId());
            read.setUserId(userId);
            read.setReadAt(now);
            boolean insertedNewRead = false;
            try {
                messageReadMapper.insert(read);
                insertedNewRead = true;
            } catch (DataIntegrityViolationException e) {
                // 并发或重复上报：uk_message_user_read 已存在
            }
            if (insertedNewRead) {
                chatMessageMapper.incrementReadCount(message.getId());
                readMessageIds.add(message.getId());
            }
        }

        conversationService.markRead(userId, request.getConversationId());

        if (!readMessageIds.isEmpty()) {
            Long conversationId = request.getConversationId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List<Long> memberIds = conversationService.listMemberIds(conversationId);
                    for (Long memberId : memberIds) {
                        if (!memberId.equals(userId)) {
                            wsPushService.pushToUser(memberId, new WsEvent<>("READ", Map.of(
                                "conversationId", conversationId,
                                "readerId", userId,
                                "messageIds", readMessageIds
                            )));
                        }
                    }
                }
            });
        }
    }

    // ==================== 送达回执 ====================

    @Transactional
    public void markDelivered(Long userId, MarkDeliveredRequest request) {
        List<Long> deliveredMessageIds = new ArrayList<>();
        for (Long messageId : request.getMessageIds()) {
            ChatMessage message = chatMessageMapper.selectById(messageId);
            if (message == null) continue;
            conversationService.assertUserInConversation(userId, message.getConversationId());
            if (userId.equals(message.getSenderId())) continue;

            long count = messageDeliverMapper.selectCount(
                new LambdaQueryWrapper<MessageDeliver>()
                    .eq(MessageDeliver::getMessageId, messageId)
                    .eq(MessageDeliver::getUserId, userId)
            );
            if (count > 0) continue;

            MessageDeliver deliver = new MessageDeliver();
            deliver.setMessageId(messageId);
            deliver.setUserId(userId);
            deliver.setDeliveredAt(LocalDateTime.now());
            boolean insertedNewDeliver = false;
            try {
                messageDeliverMapper.insert(deliver);
                insertedNewDeliver = true;
            } catch (DataIntegrityViolationException e) {
                // 并发或重复上报：uk_message_user_deliver 已存在
            }
            if (insertedNewDeliver) {
                chatMessageMapper.incrementDeliveredCount(messageId);
                deliveredMessageIds.add(messageId);
            }
        }

        if (!deliveredMessageIds.isEmpty()) {
            Long firstId = deliveredMessageIds.get(0);
            ChatMessage firstMsg = chatMessageMapper.selectById(firstId);
            if (firstMsg != null) {
                wsPushService.pushToUser(firstMsg.getSenderId(), new WsEvent<>("DELIVERED", Map.of(
                    "conversationId", firstMsg.getConversationId(),
                    "userId", userId,
                    "messageIds", deliveredMessageIds
                )));
            }
        }
    }
}
