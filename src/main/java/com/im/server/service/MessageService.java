package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.common.EsCircuitBreaker;
import com.im.server.mapper.ChatMessageMapper;
import com.im.server.mapper.MessageDeletedUserMapper;
import com.im.server.model.dto.DeleteMessagesForSelfRequest;
import com.im.server.model.es.MessageDocument;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.entity.Conversation;
import com.im.server.model.entity.ConversationMember;
import com.im.server.model.entity.MessageDeletedUser;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.model.vo.MessageSearchPageVO;
import com.im.server.service.message.MessageVOHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息查询服务 —— 负责消息的列表、搜索、删除（对自己隐藏）。
 * <p>
 * 从 {@link com.im.server.service.MessageService} 拆分而来，
 * 消息发送/编辑/撤回 → {@link com.im.server.service.message.MessageSendService}，
 * 消息互动（反应/收藏/置顶） → {@link com.im.server.service.message.MessageInteractionService}，
 * 消息回执 → {@link com.im.server.service.message.MessageReadService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final MessageDeletedUserMapper messageDeletedUserMapper;
    private final ConversationService conversationService;
    private final MessageVOHelper messageVOHelper;
    private final EsCircuitBreaker esCircuitBreaker;

    @Autowired(required = false)
    private ElasticsearchTemplate elasticsearchTemplate;

    // ==================== 列表 ====================

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
        List<ChatMessage> result = new ArrayList<>(records);
        List<Long> pageMessageIds = records.stream().map(ChatMessage::getId).toList();
        Set<Long> deletedMessageIds = pageMessageIds.isEmpty() ? Set.of()
            : messageDeletedUserMapper.selectList(
                new LambdaQueryWrapper<MessageDeletedUser>()
                    .eq(MessageDeletedUser::getUserId, userId)
                    .in(MessageDeletedUser::getMessageId, pageMessageIds)
            ).stream().map(MessageDeletedUser::getMessageId).collect(Collectors.toSet());
        if (!deletedMessageIds.isEmpty()) {
            result.removeIf(message -> deletedMessageIds.contains(message.getId()));
        }
        if (afterMessageId == null) {
            java.util.Collections.reverse(result);
        }
        MessageVOHelper.MessagePreload preload = messageVOHelper.batchPreload(userId, result);
        return result.stream().map(message -> messageVOHelper.buildMessageVO(message, userId, preload)).toList();
    }

    // ==================== 搜索 ====================

    /**
     * 用户侧消息搜索：ES 优先，降级 MySQL LIKE。
     * 支持空格分词 AND、游标分页（beforeMessageId 取更早一页）、size 默认 30、最大 100。
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

        if (elasticsearchTemplate != null) {
            if (!esCircuitBreaker.tryAcquire()) {
                log.warn("[ES] 断路器 OPEN，降级为 LIKE 搜索");
                return searchMessagesViaDB(userId, keyword, conversationId, beforeMessageId, pageSize);
            }
            try {
                MessageSearchPageVO result = searchMessagesViaES(userId, keyword, conversationId, beforeMessageId, pageSize);
                esCircuitBreaker.onSuccess();
                return result;
            } catch (Exception e) {
                esCircuitBreaker.onFailure();
                log.warn("[ES] 搜索异常 (breaker failures={}), 降级为 LIKE 搜索: {}",
                    esCircuitBreaker.getConsecutiveFailures(), e.getMessage());
                return searchMessagesViaDB(userId, keyword, conversationId, beforeMessageId, pageSize);
            }
        }

        return searchMessagesViaDB(userId, keyword, conversationId, beforeMessageId, pageSize);
    }

    private MessageSearchPageVO searchMessagesViaES(Long userId, String keyword, Long conversationId,
                                                     Long beforeMessageId, int pageSize) {
        try {
            List<Long> visibleConversationIds;
            if (conversationId != null) {
                conversationService.assertUserInConversation(userId, conversationId);
                visibleConversationIds = List.of(conversationId);
            } else {
                visibleConversationIds = conversationService.listByUserId(userId).stream()
                    .map(Conversation::getId).toList();
                if (visibleConversationIds.isEmpty()) {
                    return MessageSearchPageVO.builder()
                        .items(List.of())
                        .hasMore(false)
                        .nextBeforeMessageId(null)
                        .build();
                }
            }

            Set<Long> deletedMessageIds = new HashSet<>(messageDeletedUserMapper.selectList(
                new LambdaQueryWrapper<MessageDeletedUser>()
                    .eq(MessageDeletedUser::getUserId, userId)
            ).stream().map(MessageDeletedUser::getMessageId).toList());

            List<String> terms = java.util.Arrays.stream(keyword.split("\\s+"))
                .filter(StringUtils::isNotBlank)
                .toList();

            var boolQuery = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            for (String term : terms) {
                boolQuery.must(m -> m.match(mp -> mp.field("content").query(term)
                    .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)));
            }
            boolQuery.filter(f -> f.term(t -> t.field("recalled").value(0)));

            var fieldValues = visibleConversationIds.stream()
                .map(id -> co.elastic.clients.elasticsearch._types.FieldValue.of(id))
                .toList();
            boolQuery.filter(f -> f.terms(t -> t.field("conversationId")
                .terms(tt -> tt.value(fieldValues))));

            if (beforeMessageId != null) {
                boolQuery.filter(f -> f.range(r -> r.field("id")
                    .lt(co.elastic.clients.json.JsonData.of(beforeMessageId))));
            }

            var query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()))
                .withSort(s -> s.field(f -> f.field("id")
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .withPageable(PageRequest.of(0, pageSize + 1))
                .build();

            var searchHits = elasticsearchTemplate.search(query, MessageDocument.class);

            List<Long> docIds = searchHits.stream()
                .map(hit -> hit.getContent().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            Map<Long, ChatMessage> msgMap = docIds.isEmpty() ? Collections.emptyMap()
                : chatMessageMapper.selectBatchIds(docIds).stream()
                    .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

            List<ChatMessageVO> visible = new ArrayList<>();

            var uniqueConvIds = searchHits.stream()
                .map(hit -> hit.getContent().getConversationId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            Map<String, ConversationMember> memberMap = new java.util.HashMap<>();
            for (Long convId : uniqueConvIds) {
                try {
                    var m = conversationService.getRequiredMemberView(convId, userId);
                    memberMap.put(convId + ":" + userId, m);
                } catch (BusinessException ignored) {
                }
            }

            for (SearchHit<MessageDocument> hit : searchHits) {
                MessageDocument doc = hit.getContent();
                if (doc.getId() == null) continue;
                if (deletedMessageIds.contains(doc.getId())) continue;
                ConversationMember member = memberMap.get(doc.getConversationId() + ":" + userId);
                if (member == null) continue;
                if (member.getClearMessageId() != null && doc.getId() <= member.getClearMessageId()) continue;
                ChatMessage msg = msgMap.get(doc.getId());
                if (msg != null) {
                    visible.add(messageVOHelper.buildMessageVO(msg, userId));
                }
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
        } catch (Exception e) {
            log.warn("[ES] 搜索异常: {}", e.getMessage());
            throw e;
        }
    }

    private MessageSearchPageVO searchMessagesViaDB(Long userId, String keyword, Long conversationId,
                                                     Long beforeMessageId, int pageSize) {
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
            List<Long> conversationIds = conversationService.listByUserId(userId).stream()
                .map(Conversation::getId).toList();
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
            if (deletedMessageIds.contains(message.getId())) continue;
            var member = conversationService.getRequiredMemberView(message.getConversationId(), userId);
            if (member.getClearMessageId() != null && message.getId() <= member.getClearMessageId()) continue;
            visible.add(messageVOHelper.buildMessageVO(message, userId));
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

    // ==================== 管理员搜索 ====================

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

        if (elasticsearchTemplate != null) {
            if (!esCircuitBreaker.tryAcquire()) {
                log.warn("[ES] 断路器 OPEN，管理员搜索降级为 LIKE");
                return adminSearchMessagesViaDB(keyword, conversationId, beforeMessageId, pageSize);
            }
            try {
                MessageSearchPageVO result = adminSearchMessagesViaES(keyword, conversationId, beforeMessageId, pageSize);
                esCircuitBreaker.onSuccess();
                return result;
            } catch (Exception e) {
                esCircuitBreaker.onFailure();
                log.warn("[ES] 管理员搜索异常 (breaker failures={}), 降级为 LIKE 搜索: {}",
                    esCircuitBreaker.getConsecutiveFailures(), e.getMessage());
                return adminSearchMessagesViaDB(keyword, conversationId, beforeMessageId, pageSize);
            }
        }

        return adminSearchMessagesViaDB(keyword, conversationId, beforeMessageId, pageSize);
    }

    private MessageSearchPageVO adminSearchMessagesViaES(String keyword, Long conversationId,
                                                          Long beforeMessageId, int pageSize) {
        try {
            List<String> terms = java.util.Arrays.stream(keyword.split("\\s+"))
                .filter(StringUtils::isNotBlank)
                .toList();

            var boolQuery = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();
            for (String term : terms) {
                boolQuery.must(m -> m.matchPhrase(mp -> mp.field("content").query(term)));
            }
            if (conversationId != null) {
                boolQuery.filter(f -> f.term(t -> t.field("conversationId").value(conversationId)));
            }
            if (beforeMessageId != null) {
                boolQuery.filter(f -> f.range(r -> r.field("id")
                    .lt(co.elastic.clients.json.JsonData.of(beforeMessageId))));
            }

            var query = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQuery.build()))
                .withSort(s -> s.field(f -> f.field("id")
                    .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .withPageable(PageRequest.of(0, pageSize + 1))
                .build();

            var searchHits = elasticsearchTemplate.search(query, MessageDocument.class);
            List<ChatMessageVO> items = new ArrayList<>();
            for (SearchHit<MessageDocument> hit : searchHits) {
                MessageDocument doc = hit.getContent();
                if (doc.getId() == null) continue;
                if (Integer.valueOf(1).equals(doc.getRecalled())) continue;
                ChatMessage msg = chatMessageMapper.selectById(doc.getId());
                if (msg != null) {
                    items.add(messageVOHelper.buildMessageVO(msg, msg.getSenderId()));
                }
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
        } catch (Exception e) {
            log.warn("[ES] 管理员搜索异常: {}", e.getMessage());
            throw e;
        }
    }

    private MessageSearchPageVO adminSearchMessagesViaDB(String keyword, Long conversationId,
                                                          Long beforeMessageId, int pageSize) {
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
            if (Integer.valueOf(1).equals(m.getRecalled())) continue;
            items.add(messageVOHelper.buildMessageVO(m, m.getSenderId()));
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

    // ==================== 删除（对自己隐藏） ====================

    @Transactional
    public void deleteMessagesForSelf(Long userId, DeleteMessagesForSelfRequest request) {
        if (request.getMessageIds().isEmpty()) return;

        List<ChatMessage> messages = chatMessageMapper.selectBatchIds(request.getMessageIds());
        Map<Long, ChatMessage> msgMap = messages.stream()
            .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        Set<Long> alreadyDeleted = messageDeletedUserMapper.selectList(
            new LambdaQueryWrapper<MessageDeletedUser>()
                .eq(MessageDeletedUser::getUserId, userId)
                .in(MessageDeletedUser::getMessageId, request.getMessageIds())
        ).stream().map(MessageDeletedUser::getMessageId).collect(Collectors.toSet());

        for (Long messageId : request.getMessageIds()) {
            ChatMessage message = msgMap.get(messageId);
            if (message == null) continue;
            conversationService.assertUserInConversation(userId, message.getConversationId());
            if (!alreadyDeleted.contains(messageId)) {
                MessageDeletedUser deletedUser = new MessageDeletedUser();
                deletedUser.setUserId(userId);
                deletedUser.setMessageId(messageId);
                deletedUser.setDeletedAt(LocalDateTime.now());
                messageDeletedUserMapper.insert(deletedUser);
            }
        }
    }
}
