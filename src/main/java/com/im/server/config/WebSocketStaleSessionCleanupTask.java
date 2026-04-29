package com.im.server.config;

import com.im.server.model.vo.WsEvent;
import com.im.server.service.ConversationService;
import com.im.server.service.FriendService;
import com.im.server.service.OnlineStatusService;
import com.im.server.service.WebSocketHeartbeatService;
import com.im.server.service.WebSocketSessionRegistry;
import com.im.server.service.WsPushService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WebSocket 断线兜底清理任务。
 * 每 30 秒扫描一次所有 session，清理超过 90 秒无心跳的 session。
 * 解决客户端网络闪断、浏览器崩溃等场景下 SessionDisconnectEvent 未触发的问题。
 */
@Component
@RequiredArgsConstructor
public class WebSocketStaleSessionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(WebSocketStaleSessionCleanupTask.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketHeartbeatService heartbeatService;
    private final OnlineStatusService onlineStatusService;
    private final FriendService friendService;
    private final ConversationService conversationService;
    private final WsPushService wsPushService;

    @Scheduled(fixedDelay = WebSocketHeartbeatService.CLEANUP_INTERVAL_SECONDS * 1000)
    public void cleanupStaleSessions() {
        List<String> staleSessions = new ArrayList<>();
        for (String sessionId : sessionRegistry.getAllSessionIds()) {
            if (heartbeatService.isExpired(sessionId)) {
                staleSessions.add(sessionId);
            }
        }

        if (staleSessions.isEmpty()) {
            return;
        }

        log.warn("[WS StaleCleanup] 发现 {} 个过期 session，开始清理", staleSessions.size());

        for (String sessionId : staleSessions) {
            Long userId = sessionRegistry.getUserId(sessionId);
            sessionRegistry.unregister(sessionId);
            heartbeatService.removeHeartbeat(sessionId);

            if (userId != null && !sessionRegistry.hasActiveSessions(userId)) {
                // 该用户没有其他活跃 session，标记离线并推送 PRESENCE 事件
                if (onlineStatusService.userDisconnected(userId)) {
                    log.info("[WS StaleCleanup] 用户离线（心跳超时）userId={} session={}", userId, sessionId);
                    pushPresence(userId, false);
                }
            }
        }
    }

    /** 推送在线状态变更给好友和群成员 */
    private void pushPresence(Long userId, boolean online) {
        try {
            Set<Long> receivers = new LinkedHashSet<>(friendService.listFriendIds(userId));
            conversationService.listByUserId(userId).forEach(conversation ->
                receivers.addAll(conversationService.listVisibleMemberIds(conversation.getId()))
            );
            receivers.remove(userId);
            if (!receivers.isEmpty()) {
                wsPushService.pushToUsers(receivers, new WsEvent<>("PRESENCE", java.util.Map.of(
                    "userId", userId,
                    "online", online
                )));
            }
        } catch (Exception e) {
            log.warn("[WS StaleCleanup] 推送 PRESENCE 失败 userId={} error={}", userId, e.getMessage());
        }
    }
}
