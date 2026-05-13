package com.im.server.service.message;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MediaFileMapper;
import com.im.server.mapper.MessageDeletedUserMapper;
import com.im.server.mapper.MessagePinnedUserMapper;
import com.im.server.mapper.MessageReactionMapper;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.MediaFile;
import com.im.server.model.entity.MessageDeletedUser;
import com.im.server.model.entity.MessagePinnedUser;
import com.im.server.model.entity.MessageReaction;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.MediaMetaVO;
import com.im.server.model.vo.MessageReactionSummaryVO;
import com.im.server.model.vo.MessageReplyVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.service.ConversationService;
import com.im.server.service.MinioMediaUrlService;
import com.im.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息 VO 构建辅助类。
 * <p>
 * 从 MessageService 中分离出的 VO 构建职责，包含：
 * <ul>
 *   <li>单条消息 {@link ChatMessageVO} 构建</li>
 *   <li>批量预加载（reactions / pin / media / reply）避免 N+1</li>
 *   <li>消息可见性判断</li>
 * </ul>
 * <p>
 * 面试点：单一职责 + 批量预加载模式。将 VO 构建从业务 Service 中分离，
 * 符合 SRP；batchPreload 将 N+1 合并为 4 条批量 SQL，消除消息列表查询的性能瓶颈。
 */
@Component
@RequiredArgsConstructor
public class MessageVOHelper {

    private final ChatMessageMapper chatMessageMapper;
    private final MediaFileMapper mediaFileMapper;
    private final MessageReactionMapper messageReactionMapper;
    private final MessagePinnedUserMapper messagePinnedUserMapper;
    private final MessageDeletedUserMapper messageDeletedUserMapper;
    private final UserService userService;
    private final MinioMediaUrlService minioMediaUrlService;
    private final ConversationService conversationService;

    /**
     * 预加载上下文：用于批量加载同一批消息的反应、Pin 状态、媒体元信息和回复消息。
     */
    @Value
    public static class MessagePreload {
        Map<Long, List<MessageReaction>> reactionsByMsgId;
        Set<Long> pinnedMsgIds;
        Map<String, MediaFile> mediaByUrl;
        Map<Long, ChatMessage> replyMessages;
    }

    // ==================== buildMessageVO 系列 ====================

    public ChatMessageVO buildMessageVO(ChatMessage message, Long viewerUserId) {
        return buildMessageVO(message, viewerUserId, null);
    }

    public ChatMessageVO buildMessageVO(ChatMessage message, Long viewerUserId, MessagePreload preload) {
        UserSimpleVO sender = userService.getSimpleUser(message.getSenderId());
        MediaMetaVO mediaMeta = buildMediaMeta(message.getMediaUrl(), preload);
        List<MessageReactionSummaryVO> reactions = buildReactionSummaries(message.getId(), viewerUserId, preload);
        boolean pinnedByMe = preload != null
            ? preload.pinnedMsgIds.contains(message.getId())
            : messagePinnedUserMapper.selectCount(
                new LambdaQueryWrapper<MessagePinnedUser>()
                    .eq(MessagePinnedUser::getUserId, viewerUserId)
                    .eq(MessagePinnedUser::getMessageId, message.getId())
            ) > 0;
        MessageReplyVO replyMessage = buildReplyMessage(message, viewerUserId, preload);

        return ChatMessageVO.builder()
            .id(message.getId())
            .conversationId(message.getConversationId())
            .senderId(message.getSenderId())
            .senderNickname(sender.getNickname())
            .senderAvatar(sender.getAvatar())
            .type(message.getType())
            .content(message.getContent())
            .mediaUrl(minioMediaUrlService.presignIfOurObjectUrl(message.getMediaUrl()))
            .mediaCoverUrl(minioMediaUrlService.presignIfOurObjectUrl(message.getMediaCoverUrl()))
            .mediaMeta(mediaMeta)
            .replyMessageId(message.getReplyMessageId())
            .replyMessage(replyMessage)
            .readCount(message.getReadCount())
            .deliveredCount(message.getDeliveredCount())
            .favoriteCount(message.getFavoriteCount())
            .pinnedByMe(pinnedByMe)
            .edited(message.getEdited())
            .editedAt(message.getEditedAt())
            .mentionAll(Integer.valueOf(1).equals(message.getMentionAll()))
            .mentionUserIds(parseMentionUserIds(message.getMentionUserIds()))
            .reactions(reactions)
            .recalled(message.getRecalled())
            .recalledBy(message.getRecalledBy())
            .recalledAt(message.getRecalledAt())
            .clientMsgId(message.getClientMsgId())
            .createdAt(message.getCreatedAt())
            .build();
    }

    // ==================== Reaction Summaries ====================

    public List<MessageReactionSummaryVO> buildReactionSummaries(Long messageId, Long viewerUserId, MessagePreload preload) {
        List<MessageReaction> reactions = preload != null
            ? preload.reactionsByMsgId.getOrDefault(messageId, List.of())
            : messageReactionMapper.selectList(
                new LambdaQueryWrapper<MessageReaction>()
                    .eq(MessageReaction::getMessageId, messageId)
                    .orderByAsc(MessageReaction::getCreatedAt));
        return buildReactionSummariesFromList(reactions, viewerUserId);
    }

    public List<MessageReactionSummaryVO> buildReactionSummaries(Long messageId, Long viewerUserId) {
        return buildReactionSummaries(messageId, viewerUserId, null);
    }

    public List<MessageReactionSummaryVO> buildReactionSummariesFromList(List<MessageReaction> reactions, Long viewerUserId) {
        Map<String, List<MessageReaction>> grouped = reactions.stream()
            .collect(Collectors.groupingBy(MessageReaction::getReactionType, java.util.LinkedHashMap::new, Collectors.toList()));
        List<MessageReactionSummaryVO> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            result.add(MessageReactionSummaryVO.builder()
                .reactionType(entry.getKey())
                .count(entry.getValue().size())
                .reactedByMe(entry.getValue().stream().anyMatch(item -> item.getUserId().equals(viewerUserId)))
                .build());
        }
        return result;
    }

    // ==================== Media Meta ====================

    public MediaMetaVO buildMediaMeta(String mediaUrl, MessagePreload preload) {
        if (StringUtils.isBlank(mediaUrl)) {
            return null;
        }
        MediaFile mediaFile = preload != null
            ? preload.mediaByUrl.get(mediaUrl)
            : mediaFileMapper.selectOne(new LambdaQueryWrapper<MediaFile>().eq(MediaFile::getUrl, mediaUrl));
        if (mediaFile == null) {
            return null;
        }
        return MediaMetaVO.builder()
            .mediaType(mediaFile.getMediaType())
            .originalName(mediaFile.getOriginalName())
            .contentType(mediaFile.getContentType())
            .size(mediaFile.getSize())
            .width(mediaFile.getWidth())
            .height(mediaFile.getHeight())
            .durationSeconds(mediaFile.getDurationSeconds())
            .coverUrl(minioMediaUrlService.presignIfOurObjectUrl(mediaFile.getCoverUrl()))
            .build();
    }

    public MediaMetaVO buildMediaMeta(String mediaUrl) {
        return buildMediaMeta(mediaUrl, null);
    }

    // ==================== Reply Message ====================

    private MessageReplyVO buildReplyMessage(ChatMessage message, Long viewerUserId, MessagePreload preload) {
        if (message.getReplyMessageId() == null) {
            return null;
        }
        ChatMessage replied = preload != null
            ? preload.replyMessages.get(message.getReplyMessageId())
            : chatMessageMapper.selectById(message.getReplyMessageId());
        if (replied == null || !message.getConversationId().equals(replied.getConversationId())
            || isMessageHiddenFromUser(viewerUserId, replied)) {
            return null;
        }
        UserSimpleVO replySender = userService.getSimpleUser(replied.getSenderId());
        return MessageReplyVO.builder()
            .messageId(replied.getId())
            .senderId(replySender.getUserId())
            .senderNickname(replySender.getNickname())
            .type(replied.getType())
            .content(replied.getContent())
            .mediaUrl(minioMediaUrlService.presignIfOurObjectUrl(replied.getMediaUrl()))
            .build();
    }

    // ==================== Batch Preload ====================

    /**
     * 为一批消息批量预加载 reactions、Pin 状态、媒体文件和回复消息，
     * 避免 buildMessageVO 中的逐条查询（N+1）。
     */
    public MessagePreload batchPreload(Long userId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new MessagePreload(Map.of(), Set.of(), Map.of(), Map.of());
        }
        List<Long> msgIds = messages.stream().map(ChatMessage::getId).toList();

        // 1) 批量查询 reactions
        List<MessageReaction> allReactions = messageReactionMapper.selectList(
            new LambdaQueryWrapper<MessageReaction>()
                .in(MessageReaction::getMessageId, msgIds)
                .orderByAsc(MessageReaction::getCreatedAt));
        Map<Long, List<MessageReaction>> reactionsByMsgId = allReactions.stream()
            .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        // 2) 批量查询 Pin 状态
        Set<Long> pinnedMsgIds = messagePinnedUserMapper.selectList(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .in(MessagePinnedUser::getMessageId, msgIds)
        ).stream().map(MessagePinnedUser::getMessageId).collect(Collectors.toSet());

        // 3) 批量查询媒体文件（按 URL 去重）
        Set<String> mediaUrls = new LinkedHashSet<>();
        for (ChatMessage m : messages) {
            if (StringUtils.isNotBlank(m.getMediaUrl())) mediaUrls.add(m.getMediaUrl());
            if (StringUtils.isNotBlank(m.getMediaCoverUrl())) mediaUrls.add(m.getMediaCoverUrl());
        }
        Map<String, MediaFile> mediaByUrl = mediaUrls.isEmpty() ? Map.of()
            : mediaFileMapper.selectList(
                new LambdaQueryWrapper<MediaFile>().in(MediaFile::getUrl, mediaUrls)
            ).stream().collect(Collectors.toMap(MediaFile::getUrl, Function.identity(), (a, b) -> a));

        // 4) 批量查询回复消息
        List<Long> replyMsgIds = messages.stream()
            .map(ChatMessage::getReplyMessageId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, ChatMessage> replyMessages = replyMsgIds.isEmpty() ? Map.of()
            : chatMessageMapper.selectBatchIds(replyMsgIds).stream()
                .filter(m -> m != null)
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        return new MessagePreload(reactionsByMsgId, pinnedMsgIds, mediaByUrl, replyMessages);
    }

    // ==================== Access Control ====================

    public ChatMessage getAccessibleMessage(Long userId, Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        conversationService.assertUserInConversation(userId, message.getConversationId());
        return message;
    }

    public boolean isMessageHiddenFromUser(Long userId, ChatMessage message) {
        long deleted = messageDeletedUserMapper.selectCount(
            new LambdaQueryWrapper<MessageDeletedUser>()
                .eq(MessageDeletedUser::getUserId, userId)
                .eq(MessageDeletedUser::getMessageId, message.getId())
        );
        if (deleted > 0) {
            return true;
        }
        try {
            var member = conversationService.getRequiredMemberView(message.getConversationId(), userId);
            return member.getClearMessageId() != null && message.getId() <= member.getClearMessageId();
        } catch (BusinessException ignored) {
            return false;
        }
    }

    // ==================== Utils ====================

    private List<Long> parseMentionUserIds(String mentionUserIds) {
        if (StringUtils.isBlank(mentionUserIds)) {
            return List.of();
        }
        return java.util.Arrays.stream(mentionUserIds.split(","))
            .filter(StringUtils::isNotBlank)
            .map(Long::parseLong)
            .toList();
    }
}
