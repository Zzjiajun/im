package com.im.server.service;

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

    private String key(Long userId, Long conversationId) {
        return "im:unread:" + userId + ":" + conversationId;
    }
}
