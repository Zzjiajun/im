package com.im.server.service.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageReportMapper;
import com.im.server.model.dto.*;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.Conversation;
import com.im.server.model.entity.MessageReport;
import com.im.server.model.enums.ConversationType;
import com.im.server.model.enums.MessageType;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.WsEvent;
import com.im.server.service.*;
import com.im.server.service.message.processor.MessageTypeProcessorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息发送服务 —— 负责消息的发送、编辑、撤回、转发、合并转发、举报。
 * <p>
 * 从 {@link com.im.server.service.MessageService} 拆分而来，
 * 职责单一：只处理"消息产生与变更"相关逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendService {

    private final ChatMessageMapper chatMessageMapper;
    private final MessageReportMapper messageReportMapper;
    private final ConversationService conversationService;
    private final UnreadCacheService unreadCacheService;
    private final WsPushService wsPushService;
    private final UserService userService;
    private final BlacklistService blacklistService;
    private final NotificationService notificationService;
    private final MessagePushService messagePushService;
    private final MessageVOHelper messageVOHelper;
    private final MessageTypeProcessorRegistry processorRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private MessageSearchIndexService messageSearchIndexService;

    // ==================== 发送 ====================

    @Transactional
    public ChatMessageVO sendMessage(Long userId, SendMessageRequest request) {
        conversationService.assertUserInConversation(userId, request.getConversationId());
        List<Long> memberIds = conversationService.listMemberIds(request.getConversationId());
        assertCanSendMessage(userId, request.getConversationId(), memberIds);
        conversationService.assertUserCanSpeakInGroup(userId, request.getConversationId(), request.getType());
        processorRegistry.getProcessor(request.getType()).validate(request);
        validateMentionTargets(
            conversationService.getById(request.getConversationId()),
            request.getMentionUserIds(),
            request.getMentionAll()
        );
        validateReplyMessage(userId, request.getConversationId(), request.getReplyMessageId());

        String clientMsgId = StringUtils.trimToNull(request.getClientMsgId());
        if (clientMsgId != null) {
            ChatMessage existing = chatMessageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSenderId, userId)
                    .eq(ChatMessage::getClientMsgId, clientMsgId)
            );
            if (existing != null) {
                return messageVOHelper.buildMessageVO(existing, userId);
            }
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(request.getConversationId());
        message.setSenderId(userId);
        message.setType(request.getType().name());
        message.setContent(request.getContent());
        message.setMediaUrl(request.getMediaUrl());
        message.setMediaCoverUrl(request.getMediaCoverUrl());
        message.setReplyMessageId(request.getReplyMessageId());
        message.setReadCount(0);
        message.setDeliveredCount(0);
        message.setFavoriteCount(0);
        message.setEdited(0);
        message.setEditedAt(null);
        message.setMentionAll(Boolean.TRUE.equals(request.getMentionAll()) ? 1 : 0);
        message.setMentionUserIds(joinMentionUserIds(request.getMentionUserIds()));
        message.setRecalled(0);
        message.setClientMsgId(clientMsgId);
        message.setCreatedAt(LocalDateTime.now());
        try {
            chatMessageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            if (clientMsgId != null) {
                ChatMessage raced = chatMessageMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSenderId, userId)
                        .eq(ChatMessage::getClientMsgId, clientMsgId)
                );
                if (raced != null) {
                    return messageVOHelper.buildMessageVO(raced, userId);
                }
            }
            throw e;
        }

        conversationService.restoreConversationForUser(userId, message.getConversationId());
        conversationService.touchConversation(message.getConversationId(), message.getId(),
            processorRegistry.getProcessor(message.getType()).buildPreview(message));
        ChatMessageVO messageVO = messageVOHelper.buildMessageVO(message, userId);

        Conversation conversation = conversationService.getById(message.getConversationId());
        boolean isGroup = ConversationType.GROUP.name().equals(conversation.getType());

        List<Long> mentionTargetIds = extractMentionTargetIds(message, conversation, userId);
        eventPublisher.publishEvent(new MessagePushEvent(
            userId, messageVO, memberIds, isGroup, mentionTargetIds, message.getConversationId()
        ));

        return messageVO;
    }

    // ==================== 编辑 ====================

    @Transactional
    public ChatMessageVO editMessage(Long userId, EditMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) throw new BusinessException("消息不存在");
        if (!userId.equals(message.getSenderId())) throw new BusinessException("只能编辑自己发送的消息");
        if (!MessageType.TEXT.name().equals(message.getType())) throw new BusinessException("目前只支持编辑文本消息");
        if (Integer.valueOf(1).equals(message.getRecalled())) throw new BusinessException("撤回消息不能编辑");

        Conversation conversation = conversationService.getById(message.getConversationId());
        validateMentionTargets(conversation, request.getMentionUserIds(), request.getMentionAll());

        message.setContent(request.getContent());
        message.setEdited(1);
        message.setEditedAt(LocalDateTime.now());
        message.setMentionAll(Boolean.TRUE.equals(request.getMentionAll()) ? 1 : 0);
        message.setMentionUserIds(joinMentionUserIds(request.getMentionUserIds()));
        chatMessageMapper.updateById(message);

        ChatMessageVO messageVO = messageVOHelper.buildMessageVO(message, userId);

        if (message.getId().equals(conversation.getLastMessageId())) {
            conversationService.touchConversation(conversation.getId(), message.getId(),
                processorRegistry.getProcessor(message.getType()).buildPreview(message));
        }

        Long finalMsgId = message.getId();
        String finalContent = message.getContent();
        java.time.LocalDateTime finalEditedAt = message.getEditedAt();
        List<Long> editMemberIds = conversationService.listMemberIds(message.getConversationId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long memberId : editMemberIds) {
                    if (!memberId.equals(userId)) {
                        wsPushService.pushToUser(memberId, new WsEvent<>("EDIT", messageVO));
                    }
                }
                pushMentionEvents(userId, message, messageVO);
                if (messageSearchIndexService != null) {
                    messageSearchIndexService.updateContent(finalMsgId, finalContent, finalEditedAt);
                }
            }
        });
        return messageVO;
    }

    // ==================== 撤回 ====================

    @Transactional
    public void recallMessage(Long userId, RecallMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) throw new BusinessException("消息不存在");
        conversationService.assertUserInConversation(userId, message.getConversationId());

        Conversation conversation = conversationService.getById(message.getConversationId());
        boolean canRecall = userId.equals(message.getSenderId()) || userId.equals(conversation.getOwnerId());
        if (!canRecall) throw new BusinessException("无权撤回该消息");
        if (Integer.valueOf(1).equals(message.getRecalled())) throw new BusinessException("消息已撤回");

        message.setRecalled(1);
        message.setRecalledBy(userId);
        message.setRecalledAt(LocalDateTime.now());
        message.setContent("该消息已撤回");
        message.setMediaUrl(null);
        message.setMediaCoverUrl(null);
        chatMessageMapper.updateById(message);

        if (message.getId().equals(conversation.getLastMessageId())) {
            conversationService.touchConversation(conversation.getId(), message.getId(), "[消息已撤回]");
        }

        Long recalledConvId = message.getConversationId();
        Long recalledMsgId = message.getId();
        Long recalledByUserId = userId;
        java.time.LocalDateTime recalledAtTime = message.getRecalledAt();
        List<Long> recallMemberIds = conversationService.listMemberIds(recalledConvId);
        Map<String, Object> recallPayload = Map.of(
            "conversationId", recalledConvId,
            "messageId", recalledMsgId,
            "operatorId", userId
        );
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long memberId : recallMemberIds) {
                    if (!memberId.equals(userId)) {
                        wsPushService.pushToUser(memberId, new WsEvent<>("RECALL", recallPayload));
                    }
                }
                if (messageSearchIndexService != null) {
                    messageSearchIndexService.markRecalled(recalledMsgId, recalledByUserId, recalledAtTime);
                }
            }
        });
    }

    // ==================== 转发 ====================

    public List<ChatMessageVO> forwardMessages(Long userId, ForwardMessageRequest request) {
        ChatMessage sourceMessage = chatMessageMapper.selectById(request.getSourceMessageId());
        if (sourceMessage == null) throw new BusinessException("源消息不存在");
        conversationService.assertUserInConversation(userId, sourceMessage.getConversationId());
        if (MessageType.SYSTEM.name().equals(sourceMessage.getType())) throw new BusinessException("系统消息不能转发");
        if (Integer.valueOf(1).equals(sourceMessage.getRecalled())) throw new BusinessException("撤回消息不能转发");

        List<ChatMessageVO> result = new ArrayList<>();
        for (Long targetConversationId : request.getTargetConversationIds()) {
            SendMessageRequest smr = new SendMessageRequest();
            smr.setConversationId(targetConversationId);
            smr.setType(MessageType.valueOf(sourceMessage.getType()));
            smr.setContent(sourceMessage.getContent());
            smr.setMediaUrl(sourceMessage.getMediaUrl());
            smr.setMediaCoverUrl(sourceMessage.getMediaCoverUrl());
            smr.setReplyMessageId(null);
            ChatMessageVO sent = transactionTemplate.execute(status -> sendMessage(userId, smr));
            if (sent != null) result.add(sent);
        }
        return result;
    }

    public List<ChatMessageVO> batchForwardMessages(Long userId, BatchForwardMessagesRequest request) {
        List<ChatMessageVO> result = new ArrayList<>();
        for (Long sourceMessageId : request.getSourceMessageIds()) {
            ForwardMessageRequest fmr = new ForwardMessageRequest();
            fmr.setSourceMessageId(sourceMessageId);
            fmr.setTargetConversationIds(request.getTargetConversationIds());
            result.addAll(forwardMessages(userId, fmr));
        }
        return result;
    }

    public List<ChatMessageVO> mergeForwardMessages(Long userId, MergeForwardMessagesRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long messageId : request.getSourceMessageIds()) {
            ChatMessage m = chatMessageMapper.selectById(messageId);
            if (m == null) continue;
            conversationService.assertUserInConversation(userId, m.getConversationId());
            if (Integer.valueOf(1).equals(m.getRecalled())) continue;
            if (MessageType.SYSTEM.name().equals(m.getType())) continue;
            UserSimpleVO sender = userService.getSimpleUser(m.getSenderId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("messageId", m.getId());
            row.put("type", m.getType());
            row.put("senderNickname", sender.getNickname());
            row.put("preview", processorRegistry.getProcessor(m.getType()).buildPreview(m));
            items.add(row);
        }
        if (items.isEmpty()) throw new BusinessException("没有可合并转发的消息");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", request.getTitle());
        payload.put("items", items);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("合并消息生成失败");
        }

        List<ChatMessageVO> result = new ArrayList<>();
        for (Long targetConversationId : request.getTargetConversationIds()) {
            SendMessageRequest smr = new SendMessageRequest();
            smr.setConversationId(targetConversationId);
            smr.setType(MessageType.MERGE);
            smr.setContent(json);
            ChatMessageVO sent = transactionTemplate.execute(status -> sendMessage(userId, smr));
            if (sent != null) result.add(sent);
        }
        return result;
    }

    // ==================== 举报 ====================

    @Transactional
    public void reportMessage(Long userId, ReportMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) throw new BusinessException("消息不存在");
        conversationService.assertUserInConversation(userId, message.getConversationId());
        long count = messageReportMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MessageReport>()
                .eq(MessageReport::getReporterUserId, userId)
                .eq(MessageReport::getMessageId, request.getMessageId())
        );
        if (count > 0) throw new BusinessException("该消息已举报");
        MessageReport report = new MessageReport();
        report.setMessageId(request.getMessageId());
        report.setReporterUserId(userId);
        report.setReason(request.getReason());
        report.setRemark(request.getRemark());
        report.setCreatedAt(LocalDateTime.now());
        messageReportMapper.insert(report);
    }

    // ==================== 事件监听 ====================

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCommitted(MessagePushEvent event) {
        messagePushService.asyncPushToMembers(
            event.getSenderId(), event.getMessageVO(), event.getMemberIds(),
            event.isGroup(), event.getMentionTargetIds(), event.isMentionAll()
        );
        if (messageSearchIndexService != null) {
            try {
                ChatMessage msg = chatMessageMapper.selectById(event.getMessageVO().getId());
                if (msg != null) {
                    messageSearchIndexService.indexMessage(msg, event.getMessageVO().getSenderNickname());
                }
            } catch (Exception e) {
                log.warn("[ES] 索引失败 conversationId={} msgId={} error={}",
                    event.getConversationId(), event.getMessageVO().getId(), e.getMessage());
            }
        }
    }

    // ==================== 私有方法 ====================

    private void assertCanSendMessage(Long userId, Long conversationId, List<Long> memberIds) {
        Conversation conversation = conversationService.getById(conversationId);
        if (!ConversationType.SINGLE.name().equals(conversation.getType())) return;
        UserSimpleVO otherUser = userService.findOtherUser(userId, memberIds);
        blacklistService.assertNoBlockBetween(userId, otherUser.getUserId());
    }

    private void pushMentionEvents(Long senderId, ChatMessage message, ChatMessageVO messageVO) {
        Conversation conversation = conversationService.getById(message.getConversationId());
        if (!ConversationType.GROUP.name().equals(conversation.getType())) return;
        Set<Long> targetIds = new LinkedHashSet<>();
        if (Integer.valueOf(1).equals(message.getMentionAll())) {
            targetIds.addAll(conversationService.listVisibleMemberIds(message.getConversationId()));
        } else {
            targetIds.addAll(parseMentionUserIds(message.getMentionUserIds()));
        }
        targetIds.remove(senderId);
        for (Long targetId : targetIds) {
            if (conversationService.getMember(message.getConversationId(), targetId) == null) continue;
            if (blacklistService.isBlockedEitherWay(senderId, targetId)) continue;
            wsPushService.pushToUser(targetId, new WsEvent<>("MENTION", messageVO));
            notificationService.notifyMention(targetId, senderId, message.getContent(),
                message.getConversationId(), message.getId());
        }
    }

    private void validateMentionTargets(Conversation conversation, List<Long> mentionUserIds, Boolean mentionAll) {
        if (!Boolean.TRUE.equals(mentionAll) && (mentionUserIds == null || mentionUserIds.isEmpty())) return;
        if (!ConversationType.GROUP.name().equals(conversation.getType()))
            throw new BusinessException("只有群聊支持@功能");
        if (mentionUserIds != null && !mentionUserIds.isEmpty()) {
            Set<Long> memberIds = new HashSet<>(conversationService.listVisibleMemberIds(conversation.getId()));
            for (Long mentionUserId : mentionUserIds) {
                if (!memberIds.contains(mentionUserId))
                    throw new BusinessException("被@成员不在当前群聊中");
            }
        }
    }

    private String joinMentionUserIds(List<Long> mentionUserIds) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) return null;
        return mentionUserIds.stream().filter(Objects::nonNull).distinct()
            .map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseMentionUserIds(String mentionUserIds) {
        if (StringUtils.isBlank(mentionUserIds)) return List.of();
        return java.util.Arrays.stream(mentionUserIds.split(","))
            .filter(StringUtils::isNotBlank).map(Long::parseLong).toList();
    }

    private void validateReplyMessage(Long userId, Long conversationId, Long replyMessageId) {
        if (replyMessageId == null) return;
        ChatMessage replied = chatMessageMapper.selectById(replyMessageId);
        if (replied == null || !conversationId.equals(replied.getConversationId()))
            throw new BusinessException("回复消息不存在");
        if (messageVOHelper.isMessageHiddenFromUser(userId, replied))
            throw new BusinessException("回复消息不可见");
    }

    private List<Long> extractMentionTargetIds(ChatMessage message, Conversation conversation, Long senderId) {
        if (!ConversationType.GROUP.name().equals(conversation.getType())) return List.of();
        if (Integer.valueOf(1).equals(message.getMentionAll())) return List.of(-1L);
        String raw = message.getMentionUserIds();
        if (StringUtils.isBlank(raw)) return List.of();
        return java.util.Arrays.stream(raw.split(","))
            .filter(StringUtils::isNotBlank).map(Long::parseLong)
            .filter(id -> !id.equals(senderId)).toList();
    }
}
