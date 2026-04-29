package com.im.server.config;

import com.im.server.service.WebSocketHeartbeatService;
import com.im.server.service.WebSocketSessionRegistry;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 心跳拦截器。
 * - CONNECT: 注册 sessionId → userId 映射，记录心跳
 * - DISCONNECT: 清理映射和心跳记录
 * - 其他帧: 更新心跳时间
 *
 * 配合 WebSocketStaleSessionCleanupTask 做断线兜底：
 * 客户端网络闪断未发送 DISCONNECT 时，心跳超时后自动清理。
 */
@Component
@RequiredArgsConstructor
public class WebSocketHeartbeatInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHeartbeatInterceptor.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketHeartbeatService heartbeatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> {
                Principal principal = accessor.getUser();
                if (principal != null) {
                    try {
                        Long userId = Long.parseLong(principal.getName());
                        sessionRegistry.register(sessionId, userId);
                        heartbeatService.recordHeartbeat(sessionId);
                        log.debug("[WS Heartbeat] CONNECT session={} userId={}", sessionId, userId);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            case DISCONNECT -> {
                // 只清理心跳数据，不触发离线逻辑（交给 SessionDisconnectEvent 处理）
                heartbeatService.removeHeartbeat(sessionId);
                if (sessionRegistry.getUserId(sessionId) != null) {
                    log.debug("[WS Heartbeat] DISCONNECT session={} userId={}", sessionId, sessionRegistry.getUserId(sessionId));
                }
                sessionRegistry.unregister(sessionId);
            }
            default -> {
                // 所有其他帧（SEND、SUBSCRIBE 等）都更新心跳
                heartbeatService.recordHeartbeat(sessionId);
            }
        }

        return message;
    }
}
