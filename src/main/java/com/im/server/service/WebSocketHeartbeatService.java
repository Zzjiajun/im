package com.im.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 心跳服务。
 * 在 Redis 中记录每个 session 的最后心跳时间，用于断线兜底检测。
 */
@Service
@RequiredArgsConstructor
public class WebSocketHeartbeatService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 心跳超时阈值（秒）：超过此时间无心跳视为断线 */
    public static final long HEARTBEAT_TIMEOUT_SECONDS = 90;

    /** 清理任务执行间隔（秒） */
    public static final long CLEANUP_INTERVAL_SECONDS = 30;

    private static String key(String sessionId) {
        return "im:ws:hb:" + sessionId;
    }

    /** 记录心跳（更新最后活跃时间） */
    public void recordHeartbeat(String sessionId) {
        stringRedisTemplate.opsForValue().set(key(sessionId), String.valueOf(System.currentTimeMillis()),
                HEARTBEAT_TIMEOUT_SECONDS + CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** 获取最后心跳时间（毫秒时间戳） */
    public Long getLastHeartbeat(String sessionId) {
        String val = stringRedisTemplate.opsForValue().get(key(sessionId));
        return val != null ? Long.parseLong(val) : null;
    }

    /** 删除心跳记录 */
    public void removeHeartbeat(String sessionId) {
        stringRedisTemplate.delete(key(sessionId));
    }

    /** 检查 session 是否超时 */
    public boolean isExpired(String sessionId) {
        Long last = getLastHeartbeat(sessionId);
        if (last == null) {
            return true; // 无心跳记录视为过期
        }
        return System.currentTimeMillis() - last > HEARTBEAT_TIMEOUT_SECONDS * 1000;
    }
}
