package com.im.server.service.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageFavoriteMapper;
import com.im.server.mapper.MessagePinnedUserMapper;
import com.im.server.mapper.MessageReactionMapper;
import com.im.server.model.dto.BatchFavoriteRequest;
import com.im.server.model.dto.FavoriteMessageRequest;
import com.im.server.model.dto.ReactMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.MessageFavorite;
import com.im.server.model.entity.MessagePinnedUser;
import com.im.server.model.entity.MessageReaction;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.FavoriteMessageVO;
import com.im.server.model.vo.WsEvent;
import com.im.server.service.ConversationService;
import com.im.server.service.UserService;
import com.im.server.service.WsPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息互动服务 —— 负责消息的反应、收藏、置顶。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageInteractionService {

    private final ChatMessageMapper chatMessageMapper;
    private final MessageReactionMapper messageReactionMapper;
    private final MessageFavoriteMapper messageFavoriteMapper;
    private final MessagePinnedUserMapper messagePinnedUserMapper;
    private final ConversationService conversationService;
    private final WsPushService wsPushService;
    private final UserService userService;
    private final MessageVOHelper messageVOHelper;
    private final TransactionTemplate transactionTemplate;

    // ==================== 反应 ====================

    @Transactional
    public void reactMessage(Long userId, ReactMessageRequest request) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, request.getMessageId());
        long count = messageReactionMapper.selectCount(
            new LambdaQueryWrapper<MessageReaction>()
                .eq(MessageReaction::getMessageId, request.getMessageId())
                .eq(MessageReaction::getUserId, userId)
                .eq(MessageReaction::getReactionType, request.getReactionType())
        );
        if (count == 0) {
            MessageReaction reaction = new MessageReaction();
            reaction.setMessageId(request.getMessageId());
            reaction.setUserId(userId);
            reaction.setReactionType(request.getReactionType());
            reaction.setCreatedAt(LocalDateTime.now());
            messageReactionMapper.insert(reaction);
        }
        ChatMessageVO messageVO = messageVOHelper.buildMessageVO(message, userId);
        Long convId = message.getConversationId();
        List<Long> memberIds = conversationService.listMemberIds(convId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long memberId : memberIds) {
                    if (!memberId.equals(userId)) {
                        wsPushService.pushToUser(memberId, new WsEvent<>("REACTION", messageVO));
                    }
                }
            }
        });
    }

    @Transactional
    public void removeReaction(Long userId, ReactMessageRequest request) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, request.getMessageId());
        messageReactionMapper.delete(
            new LambdaQueryWrapper<MessageReaction>()
                .eq(MessageReaction::getMessageId, request.getMessageId())
                .eq(MessageReaction::getUserId, userId)
                .eq(MessageReaction::getReactionType, request.getReactionType())
        );
        ChatMessageVO messageVO = messageVOHelper.buildMessageVO(message, userId);
        Long convId = message.getConversationId();
        List<Long> memberIds = conversationService.listMemberIds(convId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long memberId : memberIds) {
                    if (!memberId.equals(userId)) {
                        wsPushService.pushToUser(memberId, new WsEvent<>("REACTION", messageVO));
                    }
                }
            }
        });
    }

    // ==================== 收藏 ====================

    @Transactional
    public void favoriteMessage(Long userId, FavoriteMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) throw new BusinessException("消息不存在");
        conversationService.assertUserInConversation(userId, message.getConversationId());

        long count = messageFavoriteMapper.selectCount(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .eq(MessageFavorite::getMessageId, request.getMessageId())
        );
        if (count > 0) throw new BusinessException("消息已收藏");

        MessageFavorite favorite = new MessageFavorite();
        favorite.setUserId(userId);
        favorite.setMessageId(request.getMessageId());
        favorite.setNote(request.getNote());
        favorite.setCategoryName(request.getCategoryName());
        favorite.setCreatedAt(LocalDateTime.now());
        messageFavoriteMapper.insert(favorite);

        message.setFavoriteCount(message.getFavoriteCount() + 1);
        chatMessageMapper.updateById(message);
    }

    public void batchFavoriteMessages(Long userId, BatchFavoriteRequest request) {
        for (Long messageId : request.getMessageIds()) {
            FavoriteMessageRequest fr = new FavoriteMessageRequest();
            fr.setMessageId(messageId);
            fr.setNote(request.getNote());
            fr.setCategoryName(request.getCategoryName());
            try {
                transactionTemplate.executeWithoutResult(status -> favoriteMessage(userId, fr));
            } catch (BusinessException ignored) {
                // Skip duplicates or inaccessible messages in batch mode.
            }
        }
    }

    @Transactional
    public void cancelFavorite(Long userId, Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) throw new BusinessException("消息不存在");
        int deleted = messageFavoriteMapper.delete(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .eq(MessageFavorite::getMessageId, messageId)
        );
        if (deleted == 0) throw new BusinessException("消息未收藏");
        message.setFavoriteCount(Math.max(0, message.getFavoriteCount() - 1));
        chatMessageMapper.updateById(message);
    }

    @Transactional
    public void updateFavorite(Long userId, FavoriteMessageRequest request) {
        MessageFavorite favorite = messageFavoriteMapper.selectOne(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .eq(MessageFavorite::getMessageId, request.getMessageId())
        );
        if (favorite == null) throw new BusinessException("消息未收藏");
        favorite.setNote(request.getNote());
        favorite.setCategoryName(request.getCategoryName());
        messageFavoriteMapper.updateById(favorite);
    }

    public List<FavoriteMessageVO> listFavorites(Long userId, String keyword, String categoryName) {
        List<MessageFavorite> favorites = messageFavoriteMapper.selectList(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .orderByDesc(MessageFavorite::getCreatedAt)
        );
        if (favorites.isEmpty()) return List.of();

        List<Long> msgIds = favorites.stream().map(MessageFavorite::getMessageId).toList();
        Map<Long, ChatMessage> msgMap = chatMessageMapper.selectBatchIds(msgIds).stream()
            .filter(m -> m != null)
            .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        List<FavoriteMessageVO> result = new ArrayList<>();
        for (MessageFavorite favorite : favorites) {
            ChatMessage message = msgMap.get(favorite.getMessageId());
            if (message == null) continue;
            if (StringUtils.isNotBlank(categoryName)
                && !StringUtils.equalsIgnoreCase(StringUtils.defaultString(favorite.getCategoryName()), categoryName)) {
                continue;
            }
            if (StringUtils.isNotBlank(keyword)
                && !StringUtils.containsIgnoreCase(StringUtils.defaultString(favorite.getNote()), keyword)
                && !StringUtils.containsIgnoreCase(StringUtils.defaultString(message.getContent()), keyword)) {
                continue;
            }
            result.add(FavoriteMessageVO.builder()
                .favoriteId(favorite.getId())
                .messageId(favorite.getMessageId())
                .note(favorite.getNote())
                .categoryName(favorite.getCategoryName())
                .favoriteAt(favorite.getCreatedAt())
                .message(messageVOHelper.buildMessageVO(message, userId))
                .build());
        }
        return result;
    }

    // ==================== 置顶 ====================

    @Transactional
    public void pinMessage(Long userId, Long messageId) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, messageId);
        long count = messagePinnedUserMapper.selectCount(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .eq(MessagePinnedUser::getMessageId, messageId)
        );
        if (count == 0) {
            MessagePinnedUser pinnedUser = new MessagePinnedUser();
            pinnedUser.setUserId(userId);
            pinnedUser.setMessageId(messageId);
            pinnedUser.setCreatedAt(LocalDateTime.now());
            messagePinnedUserMapper.insert(pinnedUser);
        }
        wsPushService.pushToUser(userId, new WsEvent<>("MESSAGE_PINNED", messageVOHelper.buildMessageVO(message, userId)));
    }

    @Transactional
    public void unpinMessage(Long userId, Long messageId) {
        ChatMessage message = messageVOHelper.getAccessibleMessage(userId, messageId);
        messagePinnedUserMapper.delete(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .eq(MessagePinnedUser::getMessageId, messageId)
        );
        wsPushService.pushToUser(userId, new WsEvent<>("MESSAGE_PINNED", messageVOHelper.buildMessageVO(message, userId)));
    }

    public List<ChatMessageVO> listPinnedMessages(Long userId, Long conversationId) {
        List<MessagePinnedUser> pinnedUsers = messagePinnedUserMapper.selectList(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .orderByDesc(MessagePinnedUser::getCreatedAt)
        );
        if (pinnedUsers.isEmpty()) return List.of();

        List<Long> msgIds = pinnedUsers.stream().map(MessagePinnedUser::getMessageId).toList();
        Map<Long, ChatMessage> msgMap = chatMessageMapper.selectBatchIds(msgIds).stream()
            .filter(m -> m != null)
            .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        List<ChatMessageVO> result = new ArrayList<>();
        for (MessagePinnedUser pinnedUser : pinnedUsers) {
            ChatMessage message = msgMap.get(pinnedUser.getMessageId());
            if (message == null) continue;
            if (conversationId != null && !conversationId.equals(message.getConversationId())) continue;
            conversationService.assertUserInConversation(userId, message.getConversationId());
            result.add(messageVOHelper.buildMessageVO(message, userId));
        }
        return result;
    }
}
