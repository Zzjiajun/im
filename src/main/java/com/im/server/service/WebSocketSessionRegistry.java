package com.im.server.service;

import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪 WebSocket sessionId → userId 映射。
 * 配合心跳兜底：当 SessionDisconnectEvent 未触发时，通过心跳超时清理。
 */
@Service
public class WebSocketSessionRegistry {

    /** sessionId → userId */
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    /** 反向索引：userId → 该用户的所有活跃 sessionId */
    private final ConcurrentHashMap<Long, Set<String>> userToSessions = new ConcurrentHashMap<>();

    public void register(String sessionId, Long userId) {
        sessionToUser.put(sessionId, userId);
        userToSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void unregister(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userToSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userToSessions.remove(userId);
                }
            }
        }
    }

    public Long getUserId(String sessionId) {
        return sessionToUser.get(sessionId);
    }

    public boolean hasActiveSessions(Long userId) {
        Set<String> sessions = userToSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** 获取所有已注册的 sessionId（用于心跳扫描） */
    public Set<String> getAllSessionIds() {
        return Collections.unmodifiableSet(sessionToUser.keySet());
    }

    /** 获取当前在线用户数 */
    public int activeSessionCount() {
        return sessionToUser.size();
    }
}
