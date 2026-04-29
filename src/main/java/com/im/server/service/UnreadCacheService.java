package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.mapper.UnreadHistoryMapper;
import com.im.server.model.entity.UnreadHistory;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 未读数缓存服务。
 *
 * 实时未读数存 Redis（高性能），定时持久化到 MySQL unread_history 表（防丢失）。
 * 启动时自动从 MySQL 恢复到 Redis。
 */
@Service
public class UnreadCacheService {

    private static final Logger log = LoggerFactory.getLogger(UnreadCacheService.class);
    private static final String UNREAD_KEY_PREFIX = "im:unread:";

    private final StringRedisTemplate stringRedisTemplate;
    private final UnreadHistoryMapper unreadHistoryMapper;

    public UnreadCacheService(StringRedisTemplate stringRedisTemplate,
                              UnreadHistoryMapper unreadHistoryMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.unreadHistoryMapper = unreadHistoryMapper;
    }

    /** 启动时从 MySQL 恢复未读数到 Redis */
    @PostConstruct
    public void init() {
        try {
            restoreFromDatabase();
            log.info("[Unread] 从 MySQL 恢复未读数完成");
        } catch (Exception e) {
            log.warn("[Unread] 启动恢复未读数失败: {}", e.getMessage());
        }
    }

    // ==================== Redis 实时操作 ====================

    public void incrementUnread(Long userId, Long conversationId) {
        stringRedisTemplate.opsForValue().increment(key(userId, conversationId));
    }

    public void clearUnread(Long userId, Long conversationId) {
        stringRedisTemplate.delete(key(userId, conversationId));
    }

    public Long getUnread(Long userId, Long conversationId) {
        String value = stringRedisTemplate.opsForValue().get(key(userId, conversationId));
        return value == null ? 0L : Long.parseLong(value);
    }

    // ==================== MySQL 持久化 ====================

    /**
     * 将所有 Redis 未读数持久化到 MySQL unread_history 表。
     * 使用 SCAN 游标遍历（避免 KEYS * 阻塞 Redis）。
     * 由 {@link UnreadPersistenceTask} 定时调用。
     */
    public void persistAllUnread() {
        // 使用 SCAN 替代 KEYS *，避免生产环境阻塞 Redis
        Set<String> keys = scanKeys();
        if (keys == null) {
            return;
        }

        int updated = 0;
        int inserted = 0;

        for (String redisKey : keys) {
            try {
                // key = "im:unread:{userId}:{conversationId}"
                String[] parts = redisKey.split(":");
                if (parts.length != 4) {
                    continue;
                }
                Long userId = Long.valueOf(parts[2]);
                Long conversationId = Long.valueOf(parts[3]);

                String value = stringRedisTemplate.opsForValue().get(redisKey);
                if (value == null) {
                    continue;
                }
                int count = Integer.parseInt(value);
                if (count <= 0) {
                    continue;
                }

                // Upsert：先查后改
                UnreadHistory existing = unreadHistoryMapper.selectOne(
                    new LambdaQueryWrapper<UnreadHistory>()
                        .eq(UnreadHistory::getUserId, userId)
                        .eq(UnreadHistory::getConversationId, conversationId)
                );

                if (existing != null) {
                    existing.setCount(count);
                    existing.setUpdatedAt(LocalDateTime.now());
                    unreadHistoryMapper.updateById(existing);
                    updated++;
                } else {
                    UnreadHistory history = new UnreadHistory();
                    history.setUserId(userId);
                    history.setConversationId(conversationId);
                    history.setCount(count);
                    history.setCreatedAt(LocalDateTime.now());
                    history.setUpdatedAt(LocalDateTime.now());
                    unreadHistoryMapper.insert(history);
                    inserted++;
                }
            } catch (Exception e) {
                log.warn("[Unread] 持久化单条失败 key={} error={}", redisKey, e.getMessage());
            }
        }

        log.debug("[Unread] 持久化完成: updated={}, inserted={}", updated, inserted);
    }

    /**
     * 从 MySQL 恢复未读数到 Redis（启动时调用）。
     */
    public void restoreFromDatabase() {
        List<UnreadHistory> all = unreadHistoryMapper.selectList(null);
        int restored = 0;
        for (UnreadHistory h : all) {
            if (h.getCount() != null && h.getCount() > 0) {
                stringRedisTemplate.opsForValue().set(
                    key(h.getUserId(), h.getConversationId()),
                    String.valueOf(h.getCount())
                );
                restored++;
            }
        }
        log.info("[Unread] 从 MySQL 恢复 {} 条未读数到 Redis", restored);
    }

    /**
     * 使用 SCAN 游标遍历 Redis key（替代 KEYS *，避免阻塞）。
     */
    private Set<String> scanKeys() {
        try {
            var connectionFactory = stringRedisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return null;
            }
            Set<String> keys = new HashSet<>();
            try (var connection = connectionFactory.getConnection()) {
                var options = ScanOptions.scanOptions()
                    .match(UNREAD_KEY_PREFIX + "*")
                    .count(500)
                    .build();
                var cursor = connection.keyCommands().scan(options);
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        } catch (Exception e) {
            log.warn("[Unread] SCAN 异常，降级为 KEYS: {}", e.getMessage());
            return stringRedisTemplate.keys(UNREAD_KEY_PREFIX + "*");
        }
    }

    private String key(Long userId, Long conversationId) {
        return UNREAD_KEY_PREFIX + userId + ":" + conversationId;
    }
}
