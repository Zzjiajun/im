package com.im.server.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 滑动窗口限流器 —— 使用 Redis Sorted Set 实现。
 * <p>
 * 替代固定窗口方案，解决分钟边界突发翻倍问题。
 * 每次请求加入当前时间戳作为 score，并清理窗口外的旧记录。
 * 非严格精确（存在 race condition），但对于业务限流足够。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取许可。
     *
     * @param key        Redis 键
     * @param limit      窗口内最大许可数
     * @param windowMs   窗口大小（毫秒）
     * @return true 如果未超过限制（允许通过），false 如果超过限制
     */
    public boolean tryAcquire(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        // 清理窗口外的旧记录
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 获取当前窗口内的请求数
        Long count = stringRedisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= limit) {
            return false;
        }

        // 加入当前请求（使用唯一 member 避免覆盖）
        String member = now + ":" + ThreadLocalRandom.current().nextLong();
        stringRedisTemplate.opsForZSet().add(key, member, now);
        stringRedisTemplate.expire(key, 2, TimeUnit.MINUTES);
        return true;
    }
}
