package com.im.server.service;

import com.im.server.config.ElasticsearchConfig;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.es.MessageDocument;
import com.im.server.repository.MessageSearchRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 消息搜索索引服务。
 * 在消息发送、编辑、撤回、删除时同步 ES 索引。
 * 仅当 ES 启用时生效（app.elasticsearch.enabled=true）。
 */
@Service
@ConditionalOnBean(ElasticsearchConfig.class)
@RequiredArgsConstructor
public class MessageSearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(MessageSearchIndexService.class);

    private final MessageSearchRepository messageSearchRepository;
    private final ElasticsearchConfig elasticsearchConfig;

    /**
     * 索引新消息（发送消息时调用）。
     */
    public void indexMessage(ChatMessage message, String senderNickname) {
        try {
            MessageDocument doc = buildDocument(message, senderNickname);
            messageSearchRepository.save(doc);
            log.debug("[ES] 索引消息 id={} conversationId={}", message.getId(), message.getConversationId());
        } catch (Exception e) {
            log.warn("[ES] 索引消息失败 id={} error={}", message.getId(), e.getMessage());
        }
    }

    /**
     * 更新消息内容（编辑消息时调用）。
     */
    public void updateContent(Long messageId, String newContent, LocalDateTime editedAt) {
        try {
            Optional<MessageDocument> existing = messageSearchRepository.findById(messageId);
            if (existing.isPresent()) {
                MessageDocument doc = existing.get();
                doc.setContent(newContent);
                doc.setEdited(1);
                doc.setEditedAt(editedAt);
                messageSearchRepository.save(doc);
                log.debug("[ES] 更新消息内容 id={}", messageId);
            } else {
                log.warn("[ES] 更新内容时文档不存在 id={}", messageId);
            }
        } catch (Exception e) {
            log.warn("[ES] 更新内容失败 id={} error={}", messageId, e.getMessage());
        }
    }

    /**
     * 标记消息已撤回。
     */
    public void markRecalled(Long messageId, Long recalledBy, LocalDateTime recalledAt) {
        try {
            Optional<MessageDocument> existing = messageSearchRepository.findById(messageId);
            if (existing.isPresent()) {
                MessageDocument doc = existing.get();
                doc.setRecalled(1);
                doc.setRecalledBy(recalledBy);
                doc.setRecalledAt(recalledAt);
                doc.setContent("该消息已撤回");
                messageSearchRepository.save(doc);
                log.debug("[ES] 标记撤回 id={}", messageId);
            }
        } catch (Exception e) {
            log.warn("[ES] 标记撤回失败 id={} error={}", messageId, e.getMessage());
        }
    }

    /**
     * 删除文档（用户删除消息时调用）。
     */
    public void deleteMessage(Long messageId) {
        try {
            messageSearchRepository.deleteById(messageId);
            log.debug("[ES] 删除消息 id={}", messageId);
        } catch (Exception e) {
            log.warn("[ES] 删除消息失败 id={} error={}", messageId, e.getMessage());
        }
    }

    /**
     * 构建 ES Document。
     */
    private MessageDocument buildDocument(ChatMessage message, String senderNickname) {
        return MessageDocument.builder()
            .id(message.getId())
            .conversationId(message.getConversationId())
            .senderId(message.getSenderId())
            .senderNickname(senderNickname)
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
