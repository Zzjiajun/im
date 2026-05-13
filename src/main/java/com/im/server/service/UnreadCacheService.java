package com.im.server.service;

import com.im.server.common.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UnreadCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    public UnreadCacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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

    /**
     * 将 Redis 中的未读数持久化到 MySQL unread_history 表。
     * 防止 Redis 重启导致未读数丢失。（暂为桩实现）
     */
    public void persistAllUnread() {
        // TODO: 实现 Redis → MySQL 全量同步
    }

    private String key(Long userId, Long conversationId) {
        return RedisKeyConstants.unreadCount(userId, conversationId);
    }
}
