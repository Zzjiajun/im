package com.im.server.config;

import com.im.server.model.vo.WsEvent;
import com.im.server.service.ConversationService;
import com.im.server.service.FriendService;
import com.im.server.service.OnlineStatusService;
import com.im.server.service.WsPushService;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceEventListener {

    private final OnlineStatusService onlineStatusService;
    private final FriendService friendService;
    private final ConversationService conversationService;
    private final WsPushService wsPushService;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        Long userId = extractUserId(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (userId == null) {
            return;
        }
        if (onlineStatusService.userConnected(userId)) {
            pushPresence(userId, true);
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        Long userId = extractUserId(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (userId == null) {
            return;
        }
        if (onlineStatusService.userDisconnected(userId)) {
            pushPresence(userId, false);
        }
    }

    private void pushPresence(Long userId, boolean online) {
        Set<Long> receivers = new LinkedHashSet<>(friendService.listFriendIds(userId));
        conversationService.listByUserId(userId).forEach(conversation ->
            receivers.addAll(conversationService.listVisibleMemberIds(conversation.getId()))
        );
        receivers.remove(userId);
        wsPushService.pushToUsers(receivers, new WsEvent<>("PRESENCE", java.util.Map.of(
            "userId", userId,
            "online", online
        )));
    }

    private Long extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
