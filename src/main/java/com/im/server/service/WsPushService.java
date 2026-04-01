package com.im.server.service;

import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WsPushService {

    private final SimpMessagingTemplate simpMessagingTemplate;

    public WsPushService(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void pushToUser(Long userId, Object payload) {
        simpMessagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", payload);
    }

    public void pushToUsers(Set<Long> userIds, Object payload) {
        for (Long userId : userIds) {
            if (userId != null) {
                pushToUser(userId, payload);
            }
        }
    }
}
