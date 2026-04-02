package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MediaFileMapper;
import com.im.server.mapper.MessageDeliverMapper;
import com.im.server.mapper.MessageDeletedUserMapper;
import com.im.server.mapper.MessageFavoriteMapper;
import com.im.server.mapper.MessagePinnedUserMapper;
import com.im.server.mapper.MessageReactionMapper;
import com.im.server.mapper.MessageReadMapper;
import com.im.server.mapper.MessageReportMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.server.model.dto.DeleteMessagesForSelfRequest;
import com.im.server.model.dto.BatchFavoriteRequest;
import com.im.server.model.dto.BatchForwardMessagesRequest;
import com.im.server.model.dto.MergeForwardMessagesRequest;
import com.im.server.model.dto.EditMessageRequest;
import com.im.server.model.dto.FavoriteMessageRequest;
import com.im.server.model.dto.ForwardMessageRequest;
import com.im.server.model.dto.MarkDeliveredRequest;
import com.im.server.model.dto.MarkReadRequest;
import com.im.server.model.dto.ReactMessageRequest;
import com.im.server.model.dto.RecallMessageRequest;
import com.im.server.model.dto.ReportMessageRequest;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.Conversation;
import com.im.server.model.entity.MediaFile;
import com.im.server.model.entity.MessageDeliver;
import com.im.server.model.entity.MessageDeletedUser;
import com.im.server.model.entity.MessageFavorite;
import com.im.server.model.entity.MessagePinnedUser;
import com.im.server.model.entity.MessageReaction;
import com.im.server.model.entity.MessageReport;
import com.im.server.model.entity.MessageRead;
import com.im.server.model.enums.MessageType;
import com.im.server.model.enums.ConversationType;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.FavoriteMessageVO;
import com.im.server.model.vo.MediaMetaVO;
import com.im.server.model.vo.MessageReactionSummaryVO;
import com.im.server.model.vo.MessageReceiptVO;
import com.im.server.model.vo.MessageReplyVO;
import com.im.server.model.vo.MessageSearchPageVO;
import com.im.server.model.vo.UserSimpleVO;
import com.im.server.model.vo.WsEvent;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final MediaFileMapper mediaFileMapper;
    private final MessageDeliverMapper messageDeliverMapper;
    private final MessageDeletedUserMapper messageDeletedUserMapper;
    private final MessageFavoriteMapper messageFavoriteMapper;
    private final MessagePinnedUserMapper messagePinnedUserMapper;
    private final MessageReactionMapper messageReactionMapper;
    private final MessageReadMapper messageReadMapper;
    private final MessageReportMapper messageReportMapper;
    private final ConversationService conversationService;
    private final UnreadCacheService unreadCacheService;
    private final WsPushService wsPushService;
    private final UserService userService;
    private final BlacklistService blacklistService;
    private final OfflinePushService offlinePushService;
    private final ObjectMapper objectMapper;
    private final MinioMediaUrlService minioMediaUrlService;

    @Transactional
    public ChatMessageVO sendMessage(Long userId, SendMessageRequest request) {
        conversationService.assertUserInConversation(userId, request.getConversationId());
        assertCanSendMessage(userId, request.getConversationId());
        conversationService.assertUserCanSpeakInGroup(userId, request.getConversationId(), request.getType());
        validateMessage(request.getType(), request.getContent(), request.getMediaUrl());
        validateStructuredMessage(request.getType(), request.getContent());
        validateMentionTargets(
            conversationService.getById(request.getConversationId()),
            request.getMentionUserIds(),
            request.getMentionAll()
        );

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
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        conversationService.restoreConversationForUser(userId, message.getConversationId());
        conversationService.touchConversation(message.getConversationId(), message.getId(), buildPreview(message));
        ChatMessageVO messageVO = buildMessageVO(message, userId);

        List<Long> memberIds = conversationService.listMemberIds(message.getConversationId());
        for (Long memberId : memberIds) {
            if (!memberId.equals(userId)) {
                unreadCacheService.incrementUnread(memberId, message.getConversationId());
                wsPushService.pushToUser(memberId, new WsEvent<>("MESSAGE", messageVO));
                offlinePushService.notifyNewChatMessage(memberId, messageVO);
            }
        }
        pushMentionEvents(userId, message, messageVO);
        return messageVO;
    }

    public List<ChatMessageVO> listMessages(Long userId, Long conversationId, Long beforeMessageId, Long afterMessageId,
                                            Integer size) {
        conversationService.assertUserInConversation(userId, conversationId);
        var member = conversationService.getRequiredMemberView(conversationId, userId);
        int pageSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, conversationId);
        if (member.getClearMessageId() != null) {
            wrapper.gt(ChatMessage::getId, member.getClearMessageId());
        }
        if (afterMessageId != null) {
            wrapper.gt(ChatMessage::getId, afterMessageId)
                .orderByAsc(ChatMessage::getId)
                .last("limit " + pageSize);
        } else {
            wrapper.orderByDesc(ChatMessage::getId)
                .last("limit " + pageSize);
            if (beforeMessageId != null) {
                wrapper.lt(ChatMessage::getId, beforeMessageId);
            }
        }
        List<ChatMessage> records = chatMessageMapper.selectList(wrapper);
        List<Long> deletedMessageIds = messageDeletedUserMapper.selectList(
            new LambdaQueryWrapper<MessageDeletedUser>()
                .eq(MessageDeletedUser::getUserId, userId)
        ).stream().map(MessageDeletedUser::getMessageId).toList();
        List<ChatMessage> result = new ArrayList<>(records);
        if (!deletedMessageIds.isEmpty()) {
            result.removeIf(message -> deletedMessageIds.contains(message.getId()));
        }
        if (afterMessageId == null) {
            java.util.Collections.reverse(result);
        }
        return result.stream().map(message -> buildMessageVO(message, userId)).toList();
    }

    /**
     * 用户侧消息搜索：支持空格分词 AND、游标分页（beforeMessageId 取更早一页）、size 默认 30、最大 100。
     */
    public MessageSearchPageVO searchMessages(Long userId, String keyword, Long conversationId,
                                              Long beforeMessageId, Integer size) {
        int pageSize = size == null ? 30 : Math.min(100, Math.max(1, size));
        if (StringUtils.isBlank(keyword)) {
            return MessageSearchPageVO.builder()
                .items(List.of())
                .hasMore(false)
                .nextBeforeMessageId(null)
                .build();
        }

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        for (String raw : keyword.split("\\s+")) {
            String part = StringUtils.trimToEmpty(raw);
            if (!part.isEmpty()) {
                wrapper.and(w -> w.like(ChatMessage::getContent, part));
            }
        }
        if (conversationId != null) {
            conversationService.assertUserInConversation(userId, conversationId);
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        } else {
            List<Long> conversationIds = conversationService.listByUserId(userId).stream().map(Conversation::getId).toList();
            if (conversationIds.isEmpty()) {
                return MessageSearchPageVO.builder()
                    .items(List.of())
                    .hasMore(false)
                    .nextBeforeMessageId(null)
                    .build();
            }
            wrapper.in(ChatMessage::getConversationId, conversationIds);
        }
        if (beforeMessageId != null) {
            wrapper.lt(ChatMessage::getId, beforeMessageId);
        }
        wrapper.orderByDesc(ChatMessage::getId).last("limit " + (pageSize + 1));

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        Set<Long> deletedMessageIds = new HashSet<>(messageDeletedUserMapper.selectList(
            new LambdaQueryWrapper<MessageDeletedUser>()
                .eq(MessageDeletedUser::getUserId, userId)
        ).stream().map(MessageDeletedUser::getMessageId).toList());

        List<ChatMessageVO> visible = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (deletedMessageIds.contains(message.getId())) {
                continue;
            }
            var member = conversationService.getRequiredMemberView(message.getConversationId(), userId);
            if (member.getClearMessageId() != null && message.getId() <= member.getClearMessageId()) {
                continue;
            }
            visible.add(buildMessageVO(message, userId));
        }

        boolean hasMore = visible.size() > pageSize;
        if (hasMore) {
            visible = new ArrayList<>(visible.subList(0, pageSize));
        }
        Long nextBefore = hasMore && !visible.isEmpty() ? visible.get(visible.size() - 1).getId() : null;
        return MessageSearchPageVO.builder()
            .items(visible)
            .hasMore(hasMore)
            .nextBeforeMessageId(nextBefore)
            .build();
    }

    /**
     * 管理员全库搜索（不过滤用户删除/清空视图，仅排除已撤回便于审计）。
     */
    public MessageSearchPageVO adminSearchMessages(String keyword, Long conversationId,
                                                   Long beforeMessageId, Integer size) {
        int pageSize = size == null ? 30 : Math.min(200, Math.max(1, size));
        if (StringUtils.isBlank(keyword)) {
            return MessageSearchPageVO.builder()
                .items(List.of())
                .hasMore(false)
                .nextBeforeMessageId(null)
                .build();
        }
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        for (String raw : keyword.split("\\s+")) {
            String part = StringUtils.trimToEmpty(raw);
            if (!part.isEmpty()) {
                wrapper.and(w -> w.like(ChatMessage::getContent, part));
            }
        }
        if (conversationId != null) {
            wrapper.eq(ChatMessage::getConversationId, conversationId);
        }
        if (beforeMessageId != null) {
            wrapper.lt(ChatMessage::getId, beforeMessageId);
        }
        wrapper.orderByDesc(ChatMessage::getId).last("limit " + (pageSize + 1));
        List<ChatMessage> rows = chatMessageMapper.selectList(wrapper);
        List<ChatMessageVO> items = new ArrayList<>();
        for (ChatMessage m : rows) {
            if (Integer.valueOf(1).equals(m.getRecalled())) {
                continue;
            }
            items.add(buildMessageVO(m, m.getSenderId()));
        }
        boolean hasMore = items.size() > pageSize;
        if (hasMore) {
            items = new ArrayList<>(items.subList(0, pageSize));
        }
        Long nextBefore = hasMore && !items.isEmpty() ? items.get(items.size() - 1).getId() : null;
        return MessageSearchPageVO.builder()
            .items(items)
            .hasMore(hasMore)
            .nextBeforeMessageId(nextBefore)
            .build();
    }

    public List<MessageReceiptVO> listReadReceipts(Long userId, Long messageId) {
        ChatMessage message = getAccessibleMessage(userId, messageId);
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
        ChatMessage message = getAccessibleMessage(userId, messageId);
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
    public void favoriteMessage(Long userId, FavoriteMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        conversationService.assertUserInConversation(userId, message.getConversationId());

        long count = messageFavoriteMapper.selectCount(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .eq(MessageFavorite::getMessageId, request.getMessageId())
        );
        if (count > 0) {
            throw new BusinessException("消息已收藏");
        }

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

    @Transactional
    public void batchFavoriteMessages(Long userId, BatchFavoriteRequest request) {
        for (Long messageId : request.getMessageIds()) {
            FavoriteMessageRequest favoriteRequest = new FavoriteMessageRequest();
            favoriteRequest.setMessageId(messageId);
            favoriteRequest.setNote(request.getNote());
            favoriteRequest.setCategoryName(request.getCategoryName());
            try {
                favoriteMessage(userId, favoriteRequest);
            } catch (BusinessException ignored) {
                // Skip duplicates or inaccessible messages in batch mode.
            }
        }
    }

    @Transactional
    public void cancelFavorite(Long userId, Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        int deleted = messageFavoriteMapper.delete(
            new LambdaQueryWrapper<MessageFavorite>()
                .eq(MessageFavorite::getUserId, userId)
                .eq(MessageFavorite::getMessageId, messageId)
        );
        if (deleted == 0) {
            throw new BusinessException("消息未收藏");
        }
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
        if (favorite == null) {
            throw new BusinessException("消息未收藏");
        }
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
        List<FavoriteMessageVO> result = new ArrayList<>();
        for (MessageFavorite favorite : favorites) {
            ChatMessage message = chatMessageMapper.selectById(favorite.getMessageId());
            if (message == null) {
                continue;
            }
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
                .message(buildMessageVO(message, userId))
                .build());
        }
        return result;
    }

    @Transactional
    public void pinMessage(Long userId, Long messageId) {
        ChatMessage message = getAccessibleMessage(userId, messageId);
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
        wsPushService.pushToUser(userId, new WsEvent<>("MESSAGE_PINNED", buildMessageVO(message, userId)));
    }

    @Transactional
    public void unpinMessage(Long userId, Long messageId) {
        ChatMessage message = getAccessibleMessage(userId, messageId);
        messagePinnedUserMapper.delete(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .eq(MessagePinnedUser::getMessageId, messageId)
        );
        wsPushService.pushToUser(userId, new WsEvent<>("MESSAGE_PINNED", buildMessageVO(message, userId)));
    }

    public List<ChatMessageVO> listPinnedMessages(Long userId, Long conversationId) {
        List<MessagePinnedUser> pinnedUsers = messagePinnedUserMapper.selectList(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, userId)
                .orderByDesc(MessagePinnedUser::getCreatedAt)
        );
        List<ChatMessageVO> result = new ArrayList<>();
        for (MessagePinnedUser pinnedUser : pinnedUsers) {
            ChatMessage message = chatMessageMapper.selectById(pinnedUser.getMessageId());
            if (message == null) {
                continue;
            }
            if (conversationId != null && !conversationId.equals(message.getConversationId())) {
                continue;
            }
            conversationService.assertUserInConversation(userId, message.getConversationId());
            result.add(buildMessageVO(message, userId));
        }
        return result;
    }

    @Transactional
    public ChatMessageVO editMessage(Long userId, EditMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        if (!userId.equals(message.getSenderId())) {
            throw new BusinessException("只能编辑自己发送的消息");
        }
        if (!MessageType.TEXT.name().equals(message.getType())) {
            throw new BusinessException("目前只支持编辑文本消息");
        }
        if (Integer.valueOf(1).equals(message.getRecalled())) {
            throw new BusinessException("撤回消息不能编辑");
        }

        Conversation conversation = conversationService.getById(message.getConversationId());
        validateMentionTargets(conversation, request.getMentionUserIds(), request.getMentionAll());

        message.setContent(request.getContent());
        message.setEdited(1);
        message.setEditedAt(LocalDateTime.now());
        message.setMentionAll(Boolean.TRUE.equals(request.getMentionAll()) ? 1 : 0);
        message.setMentionUserIds(joinMentionUserIds(request.getMentionUserIds()));
        chatMessageMapper.updateById(message);

        if (message.getId().equals(conversation.getLastMessageId())) {
            conversationService.touchConversation(conversation.getId(), message.getId(), buildPreview(message));
        }

        ChatMessageVO messageVO = buildMessageVO(message, userId);
        for (Long memberId : conversationService.listMemberIds(message.getConversationId())) {
            if (!memberId.equals(userId)) {
                wsPushService.pushToUser(memberId, new WsEvent<>("EDIT", messageVO));
            }
        }
        pushMentionEvents(userId, message, messageVO);
        return messageVO;
    }

    @Transactional
    public void reactMessage(Long userId, ReactMessageRequest request) {
        ChatMessage message = getAccessibleMessage(userId, request.getMessageId());
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
        ChatMessageVO messageVO = buildMessageVO(message, userId);
        for (Long memberId : conversationService.listMemberIds(message.getConversationId())) {
            if (!memberId.equals(userId)) {
                wsPushService.pushToUser(memberId, new WsEvent<>("REACTION", messageVO));
            }
        }
    }

    @Transactional
    public void removeReaction(Long userId, ReactMessageRequest request) {
        ChatMessage message = getAccessibleMessage(userId, request.getMessageId());
        messageReactionMapper.delete(
            new LambdaQueryWrapper<MessageReaction>()
                .eq(MessageReaction::getMessageId, request.getMessageId())
                .eq(MessageReaction::getUserId, userId)
                .eq(MessageReaction::getReactionType, request.getReactionType())
        );
        ChatMessageVO messageVO = buildMessageVO(message, userId);
        for (Long memberId : conversationService.listMemberIds(message.getConversationId())) {
            if (!memberId.equals(userId)) {
                wsPushService.pushToUser(memberId, new WsEvent<>("REACTION", messageVO));
            }
        }
    }

    @Transactional
    public void reportMessage(Long userId, ReportMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        conversationService.assertUserInConversation(userId, message.getConversationId());
        long count = messageReportMapper.selectCount(
            new LambdaQueryWrapper<MessageReport>()
                .eq(MessageReport::getReporterUserId, userId)
                .eq(MessageReport::getMessageId, request.getMessageId())
        );
        if (count > 0) {
            throw new BusinessException("该消息已举报");
        }
        MessageReport report = new MessageReport();
        report.setMessageId(request.getMessageId());
        report.setReporterUserId(userId);
        report.setReason(request.getReason());
        report.setRemark(request.getRemark());
        report.setCreatedAt(LocalDateTime.now());
        messageReportMapper.insert(report);
    }

    @Transactional
    public void deleteMessagesForSelf(Long userId, DeleteMessagesForSelfRequest request) {
        for (Long messageId : request.getMessageIds()) {
            ChatMessage message = chatMessageMapper.selectById(messageId);
            if (message == null) {
                continue;
            }
            conversationService.assertUserInConversation(userId, message.getConversationId());
            long count = messageDeletedUserMapper.selectCount(
                new LambdaQueryWrapper<MessageDeletedUser>()
                    .eq(MessageDeletedUser::getUserId, userId)
                    .eq(MessageDeletedUser::getMessageId, messageId)
            );
            if (count == 0) {
                MessageDeletedUser deletedUser = new MessageDeletedUser();
                deletedUser.setUserId(userId);
                deletedUser.setMessageId(messageId);
                deletedUser.setDeletedAt(LocalDateTime.now());
                messageDeletedUserMapper.insert(deletedUser);
            }
        }
    }

    @Transactional
    public void markDelivered(Long userId, MarkDeliveredRequest request) {
        List<Long> deliveredMessageIds = new ArrayList<>();
        for (Long messageId : request.getMessageIds()) {
            ChatMessage message = chatMessageMapper.selectById(messageId);
            if (message == null) {
                continue;
            }
            conversationService.assertUserInConversation(userId, message.getConversationId());
            if (userId.equals(message.getSenderId())) {
                continue;
            }

            long count = messageDeliverMapper.selectCount(
                new LambdaQueryWrapper<MessageDeliver>()
                    .eq(MessageDeliver::getMessageId, messageId)
                    .eq(MessageDeliver::getUserId, userId)
            );
            if (count > 0) {
                continue;
            }

            MessageDeliver deliver = new MessageDeliver();
            deliver.setMessageId(messageId);
            deliver.setUserId(userId);
            deliver.setDeliveredAt(LocalDateTime.now());
            boolean insertedNewDeliver = false;
            try {
                messageDeliverMapper.insert(deliver);
                insertedNewDeliver = true;
            } catch (RuntimeException e) {
                if (!isDuplicateMessageDeliver(e)) {
                    throw e;
                }
                // 并发或客户端重复上报：uk_message_user_deliver 已存在，不重复累加 delivered_count
            }
            if (insertedNewDeliver) {
                message.setDeliveredCount(message.getDeliveredCount() + 1);
                chatMessageMapper.updateById(message);
                deliveredMessageIds.add(messageId);
            }
        }

        if (!deliveredMessageIds.isEmpty()) {
            Long senderId = null;
            Long conversationId = null;
            for (Long messageId : deliveredMessageIds) {
                ChatMessage message = chatMessageMapper.selectById(messageId);
                if (message != null) {
                    senderId = message.getSenderId();
                    conversationId = message.getConversationId();
                    break;
                }
            }
            if (senderId != null && conversationId != null) {
                wsPushService.pushToUser(senderId, new WsEvent<>("DELIVERED", Map.of(
                    "conversationId", conversationId,
                    "userId", userId,
                    "messageIds", deliveredMessageIds
                )));
            }
        }
    }

    /** 送达记录并发插入或重复请求时，MyBatis/Spring 可能抛出不同层级的包装异常 */
    private static boolean isDuplicateMessageDeliver(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof DuplicateKeyException) {
                return true;
            }
            if (t instanceof SQLIntegrityConstraintViolationException le) {
                String msg = le.getMessage();
                return msg != null && msg.contains("uk_message_user_deliver");
            }
        }
        return false;
    }

    @Transactional
    public List<ChatMessageVO> forwardMessages(Long userId, ForwardMessageRequest request) {
        ChatMessage sourceMessage = chatMessageMapper.selectById(request.getSourceMessageId());
        if (sourceMessage == null) {
            throw new BusinessException("源消息不存在");
        }
        conversationService.assertUserInConversation(userId, sourceMessage.getConversationId());
        if (MessageType.SYSTEM.name().equals(sourceMessage.getType())) {
            throw new BusinessException("系统消息不能转发");
        }
        if (Integer.valueOf(1).equals(sourceMessage.getRecalled())) {
            throw new BusinessException("撤回消息不能转发");
        }

        List<ChatMessageVO> result = new ArrayList<>();
        for (Long targetConversationId : request.getTargetConversationIds()) {
            SendMessageRequest sendMessageRequest = new SendMessageRequest();
            sendMessageRequest.setConversationId(targetConversationId);
            sendMessageRequest.setType(MessageType.valueOf(sourceMessage.getType()));
            sendMessageRequest.setContent(sourceMessage.getContent());
            sendMessageRequest.setMediaUrl(sourceMessage.getMediaUrl());
            sendMessageRequest.setMediaCoverUrl(sourceMessage.getMediaCoverUrl());
            sendMessageRequest.setReplyMessageId(null);
            result.add(sendMessage(userId, sendMessageRequest));
        }
        return result;
    }

    @Transactional
    public List<ChatMessageVO> batchForwardMessages(Long userId, BatchForwardMessagesRequest request) {
        List<ChatMessageVO> result = new ArrayList<>();
        for (Long sourceMessageId : request.getSourceMessageIds()) {
            ForwardMessageRequest forwardMessageRequest = new ForwardMessageRequest();
            forwardMessageRequest.setSourceMessageId(sourceMessageId);
            forwardMessageRequest.setTargetConversationIds(request.getTargetConversationIds());
            result.addAll(forwardMessages(userId, forwardMessageRequest));
        }
        return result;
    }

    @Transactional
    public List<ChatMessageVO> mergeForwardMessages(Long userId, MergeForwardMessagesRequest request) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Long messageId : request.getSourceMessageIds()) {
            ChatMessage m = chatMessageMapper.selectById(messageId);
            if (m == null) {
                continue;
            }
            conversationService.assertUserInConversation(userId, m.getConversationId());
            if (Integer.valueOf(1).equals(m.getRecalled())) {
                continue;
            }
            if (MessageType.SYSTEM.name().equals(m.getType())) {
                continue;
            }
            UserSimpleVO sender = userService.getSimpleUser(m.getSenderId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("messageId", m.getId());
            row.put("type", m.getType());
            row.put("senderNickname", sender.getNickname());
            row.put("preview", buildPreview(m));
            items.add(row);
        }
        if (items.isEmpty()) {
            throw new BusinessException("没有可合并转发的消息");
        }
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
            SendMessageRequest sendMessageRequest = new SendMessageRequest();
            sendMessageRequest.setConversationId(targetConversationId);
            sendMessageRequest.setType(MessageType.MERGE);
            sendMessageRequest.setContent(json);
            result.add(sendMessage(userId, sendMessageRequest));
        }
        return result;
    }

    @Transactional
    public void markRead(Long userId, MarkReadRequest request) {
        conversationService.assertUserInConversation(userId, request.getConversationId());

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getConversationId, request.getConversationId())
            .ne(ChatMessage::getSenderId, userId)
            .orderByAsc(ChatMessage::getId);
        if (request.getLastReadMessageId() != null) {
            wrapper.le(ChatMessage::getId, request.getLastReadMessageId());
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        List<Long> readMessageIds = new ArrayList<>();
        for (ChatMessage message : messages) {
            long count = messageReadMapper.selectCount(
                new LambdaQueryWrapper<MessageRead>()
                    .eq(MessageRead::getMessageId, message.getId())
                    .eq(MessageRead::getUserId, userId)
            );
            if (count == 0) {
                MessageRead read = new MessageRead();
                read.setMessageId(message.getId());
                read.setUserId(userId);
                read.setReadAt(LocalDateTime.now());
                messageReadMapper.insert(read);

                message.setReadCount(message.getReadCount() + 1);
                chatMessageMapper.updateById(message);
                readMessageIds.add(message.getId());
            }
        }

        conversationService.markRead(userId, request.getConversationId());
        if (!readMessageIds.isEmpty()) {
            for (Long memberId : conversationService.listMemberIds(request.getConversationId())) {
                if (!memberId.equals(userId)) {
                    wsPushService.pushToUser(memberId, new WsEvent<>("READ", Map.of(
                        "conversationId", request.getConversationId(),
                        "readerId", userId,
                        "messageIds", readMessageIds
                    )));
                }
            }
        }
    }

    @Transactional
    public void recallMessage(Long userId, RecallMessageRequest request) {
        ChatMessage message = chatMessageMapper.selectById(request.getMessageId());
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        conversationService.assertUserInConversation(userId, message.getConversationId());

        Conversation conversation = conversationService.getById(message.getConversationId());
        boolean canRecall = userId.equals(message.getSenderId()) || userId.equals(conversation.getOwnerId());
        if (!canRecall) {
            throw new BusinessException("无权撤回该消息");
        }
        if (Integer.valueOf(1).equals(message.getRecalled())) {
            throw new BusinessException("消息已撤回");
        }

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

        for (Long memberId : conversationService.listMemberIds(message.getConversationId())) {
            if (!memberId.equals(userId)) {
                wsPushService.pushToUser(memberId, new WsEvent<>("RECALL", Map.of(
                    "conversationId", message.getConversationId(),
                    "messageId", message.getId(),
                    "operatorId", userId
                )));
            }
        }
    }

    private void validateMessage(MessageType type, String content, String mediaUrl) {
        if (type == MessageType.SYSTEM) {
            throw new BusinessException("不能发送系统消息");
        }
        if (type == MessageType.TEXT && StringUtils.isBlank(content)) {
            throw new BusinessException("文本消息内容不能为空");
        }
        if ((type == MessageType.IMAGE || type == MessageType.VIDEO || type == MessageType.VOICE || type == MessageType.FILE)
            && StringUtils.isBlank(mediaUrl)) {
            throw new BusinessException("媒体消息地址不能为空");
        }
        if (type == MessageType.EMOJI && StringUtils.isBlank(content) && StringUtils.isBlank(mediaUrl)) {
            throw new BusinessException("表情消息内容不能为空");
        }
    }

    private void validateStructuredMessage(MessageType type, String content) {
        if (type != MessageType.MERGE && type != MessageType.LOCATION && type != MessageType.CONTACT) {
            return;
        }
        if (StringUtils.isBlank(content)) {
            throw new BusinessException("消息内容不能为空");
        }
        try {
            JsonNode n = objectMapper.readTree(content);
            switch (type) {
                case MERGE -> {
                    if (!n.hasNonNull("title") || !n.get("title").isTextual()) {
                        throw new BusinessException("合并消息格式不正确");
                    }
                    if (!n.has("items") || !n.get("items").isArray() || n.get("items").isEmpty()) {
                        throw new BusinessException("合并消息格式不正确");
                    }
                }
                case LOCATION -> {
                    if (!n.has("lat") || !n.has("lng")) {
                        throw new BusinessException("位置消息格式不正确");
                    }
                }
                case CONTACT -> {
                    if (!n.has("userId")) {
                        throw new BusinessException("名片消息格式不正确");
                    }
                    userService.getSimpleUser(n.get("userId").asLong());
                }
                default -> {
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("消息内容格式不正确");
        }
    }

    private String buildPreview(ChatMessage message) {
        if (StringUtils.isNotBlank(message.getContent())) {
            return StringUtils.abbreviate(message.getContent(), 50);
        }
        return switch (MessageType.valueOf(message.getType())) {
            case IMAGE -> "[图片]";
            case VIDEO -> "[视频]";
            case VOICE -> "[语音]";
            case FILE -> "[文件]";
            case EMOJI -> "[表情]";
            case FAVORITE_CARD -> "[收藏]";
            case MERGE -> "[聊天记录]";
            case LOCATION -> "[位置]";
            case CONTACT -> "[名片]";
            case SYSTEM -> "[系统消息]";
            default -> "[消息]";
        };
    }

    private void assertCanSendMessage(Long userId, Long conversationId) {
        Conversation conversation = conversationService.getById(conversationId);
        if (!ConversationType.SINGLE.name().equals(conversation.getType())) {
            return;
        }
        UserSimpleVO otherUser = userService.findOtherUser(userId, conversationService.listMemberIds(conversationId));
        blacklistService.assertNoBlockBetween(userId, otherUser.getUserId());
    }

    private void pushMentionEvents(Long senderId, ChatMessage message, ChatMessageVO messageVO) {
        Conversation conversation = conversationService.getById(message.getConversationId());
        if (!ConversationType.GROUP.name().equals(conversation.getType())) {
            return;
        }
        Set<Long> targetIds = new LinkedHashSet<>();
        if (Integer.valueOf(1).equals(message.getMentionAll())) {
            targetIds.addAll(conversationService.listVisibleMemberIds(message.getConversationId()));
        } else {
            targetIds.addAll(parseMentionUserIds(message.getMentionUserIds()));
        }
        targetIds.remove(senderId);
        for (Long targetId : targetIds) {
            if (conversationService.getMember(message.getConversationId(), targetId) == null) {
                continue;
            }
            if (blacklistService.isBlockedEitherWay(senderId, targetId)) {
                continue;
            }
            wsPushService.pushToUser(targetId, new WsEvent<>("MENTION", messageVO));
        }
    }

    private void validateMentionTargets(Conversation conversation, List<Long> mentionUserIds, Boolean mentionAll) {
        if (!Boolean.TRUE.equals(mentionAll) && (mentionUserIds == null || mentionUserIds.isEmpty())) {
            return;
        }
        if (!ConversationType.GROUP.name().equals(conversation.getType())) {
            throw new BusinessException("只有群聊支持@功能");
        }
        if (mentionUserIds != null && !mentionUserIds.isEmpty()) {
            Set<Long> memberIds = new HashSet<>(conversationService.listVisibleMemberIds(conversation.getId()));
            for (Long mentionUserId : mentionUserIds) {
                if (!memberIds.contains(mentionUserId)) {
                    throw new BusinessException("被@成员不在当前群聊中");
                }
            }
        }
    }

    private String joinMentionUserIds(List<Long> mentionUserIds) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return null;
        }
        return mentionUserIds.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    private List<Long> parseMentionUserIds(String mentionUserIds) {
        if (StringUtils.isBlank(mentionUserIds)) {
            return List.of();
        }
        return java.util.Arrays.stream(mentionUserIds.split(","))
            .filter(StringUtils::isNotBlank)
            .map(Long::parseLong)
            .toList();
    }

    private ChatMessageVO buildMessageVO(ChatMessage message, Long viewerUserId) {
        UserSimpleVO sender = userService.getSimpleUser(message.getSenderId());
        MediaMetaVO mediaMeta = buildMediaMeta(message.getMediaUrl());
        List<MessageReactionSummaryVO> reactions = buildReactionSummaries(message.getId(), viewerUserId);
        boolean pinnedByMe = messagePinnedUserMapper.selectCount(
            new LambdaQueryWrapper<MessagePinnedUser>()
                .eq(MessagePinnedUser::getUserId, viewerUserId)
                .eq(MessagePinnedUser::getMessageId, message.getId())
        ) > 0;
        MessageReplyVO replyMessage = null;
        if (message.getReplyMessageId() != null) {
            ChatMessage replied = chatMessageMapper.selectById(message.getReplyMessageId());
            if (replied != null) {
                UserSimpleVO replySender = userService.getSimpleUser(replied.getSenderId());
                replyMessage = MessageReplyVO.builder()
                    .messageId(replied.getId())
                    .senderId(replySender.getUserId())
                    .senderNickname(replySender.getNickname())
                    .type(replied.getType())
                    .content(replied.getContent())
                    .mediaUrl(minioMediaUrlService.presignIfOurObjectUrl(replied.getMediaUrl()))
                    .build();
            }
        }

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
            .createdAt(message.getCreatedAt())
            .build();
    }

    private List<MessageReactionSummaryVO> buildReactionSummaries(Long messageId, Long viewerUserId) {
        List<MessageReaction> reactions = messageReactionMapper.selectList(
            new LambdaQueryWrapper<MessageReaction>()
                .eq(MessageReaction::getMessageId, messageId)
                .orderByAsc(MessageReaction::getCreatedAt)
        );
        java.util.Map<String, List<MessageReaction>> grouped = reactions.stream()
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

    private MediaMetaVO buildMediaMeta(String mediaUrl) {
        if (StringUtils.isBlank(mediaUrl)) {
            return null;
        }
        MediaFile mediaFile = mediaFileMapper.selectOne(
            new LambdaQueryWrapper<MediaFile>().eq(MediaFile::getUrl, mediaUrl)
        );
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

    private ChatMessage getAccessibleMessage(Long userId, Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("消息不存在");
        }
        conversationService.assertUserInConversation(userId, message.getConversationId());
        return message;
    }
}
