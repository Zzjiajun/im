package com.im.server.service;

import com.im.server.model.vo.WsEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeEventService {

    private final ConversationService conversationService;
    private final WsPushService wsPushService;

    public void sendTyping(Long userId, Long conversationId, boolean typing) {
        conversationService.assertUserInConversation(userId, conversationId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("typing", typing);

        List<Long> memberIds = conversationService.listVisibleMemberIds(conversationId);
        for (Long memberId : memberIds) {
            if (!memberId.equals(userId)) {
                wsPushService.pushToUser(memberId, new WsEvent<>("TYPING", payload));
            }
        }
    }
}
