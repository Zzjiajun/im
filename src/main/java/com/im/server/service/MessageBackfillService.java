package com.im.server.service;

import com.im.server.config.ElasticsearchConfig;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.es.MessageDocument;
import com.im.server.repository.MessageSearchRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

/**
 * 历史消息回填服务。
 * 从 MySQL 分批读取消息并索引到 ES。
 * 可通过 API 或命令行触发。
 */
@Service
@ConditionalOnBean(ElasticsearchConfig.class)
@RequiredArgsConstructor
public class MessageBackfillService {

    private static final Logger log = LoggerFactory.getLogger(MessageBackfillService.class);

    private final MessageSearchRepository messageSearchRepository;
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final com.im.server.mapper.ChatMessageMapper chatMessageMapper;

    /**
     * 分批回填所有历史消息。
     *
     * @param batchSize  每批处理数量
     * @param fromId     起始消息 ID（含）
     * @param maxId      最大消息 ID（含）
     * @return 实际索引的消息数量
     */
    public long backfillAll(int batchSize, long fromId, long maxId) {
        long total = 0;
        long currentMax = fromId;
        boolean hasMore = true;

        while (hasMore && currentMax <= maxId) {
            List<ChatMessage> batch = loadBatch(currentMax, batchSize);
            if (batch.isEmpty()) {
                hasMore = false;
                break;
            }

            List<MessageDocument> docs = batch.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());

            messageSearchRepository.saveAll(docs);
            total += batch.size();
            currentMax = batch.get(batch.size() - 1).getId();

            log.info("[ES Backfill] 已回填 {} 条 (至 ID {})", total, currentMax);

            if (batch.size() < batchSize) {
                hasMore = false;
            }
        }

        log.info("[ES Backfill] 回填完成，共 {} 条", total);
        return total;
    }

    /**
     * 从 MySQL 加载一批消息。
     */
    private List<ChatMessage> loadBatch(long afterId, int batchSize) {
        return chatMessageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                .gt(ChatMessage::getId, afterId)
                .orderByAsc(ChatMessage::getId)
                .last("limit " + batchSize)
        );
    }

    /**
     * 检查 ES 中是否已有数据。
     */
    public long countIndexed() {
        try {
            var query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withPageable(PageRequest.of(0, 1))
                .build();
            return elasticsearchTemplate.count(query, MessageDocument.class);
        } catch (Exception e) {
            log.warn("[ES Backfill] 查询索引计数失败: {}", e.getMessage());
            return -1;
        }
    }

    private MessageDocument toDocument(ChatMessage message) {
        return MessageDocument.builder()
            .id(message.getId())
            .conversationId(message.getConversationId())
            .senderId(message.getSenderId())
            .type(message.getType())
            .content(message.getContent())
            .readCount(message.getReadCount())
            .deliveredCount(message.getDeliveredCount())
            .favoriteCount(message.getFavoriteCount())
            .edited(message.getEdited())
            .editedAt(message.getEditedAt())
            .mentionAll(message.getMentionAll())
            .mentionUserIds(parseMentionUserIds(message.getMentionUserIds()))
            .recalled(message.getRecalled())
            .recalledBy(message.getRecalledBy())
            .recalledAt(message.getRecalledAt())
            .createdAt(message.getCreatedAt())
            .build();
    }

    private List<String> parseMentionUserIds(String mentionUserIds) {
        if (StringUtils.isBlank(mentionUserIds)) {
            return List.of();
        }
        return Arrays.stream(mentionUserIds.split(","))
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toList());
    }
}
