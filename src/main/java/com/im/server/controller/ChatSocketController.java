package com.im.server.controller;

import com.im.server.model.dto.MarkDeliveredRequest;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.dto.WsChatPayload;
import com.im.server.model.dto.WsDeliverPayload;
import com.im.server.model.dto.WsTypingPayload;
import com.im.server.model.vo.ChatMessageVO;
import com.im.server.service.RealtimeEventService;
import com.im.server.service.message.MessageReadService;
import com.im.server.service.message.MessageSendService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {

    private final MessageSendService messageSendService;
    private final MessageReadService messageReadService;
    private final RealtimeEventService realtimeEventService;

    @MessageMapping("/chat.send")
    public ChatMessageVO send(@Payload WsChatPayload payload, Principal principal) {
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(payload.getConversationId());
        request.setType(payload.getType());
        request.setContent(payload.getContent());
        request.setMediaUrl(payload.getMediaUrl());
        request.setMediaCoverUrl(payload.getMediaCoverUrl());
        request.setReplyMessageId(payload.getReplyMessageId());
        request.setMentionAll(payload.getMentionAll());
        request.setMentionUserIds(payload.getMentionUserIds());
        request.setClientMsgId(payload.getClientMsgId());
        return messageSendService.sendMessage(Long.parseLong(principal.getName()), request);
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload WsTypingPayload payload, Principal principal) {
        realtimeEventService.sendTyping(
            Long.parseLong(principal.getName()),
            payload.getConversationId(),
            Boolean.TRUE.equals(payload.getTyping())
        );
    }

    @MessageMapping("/chat.deliver")
    public void deliver(@Payload WsDeliverPayload payload, Principal principal) {
        var request = new MarkDeliveredRequest();
        request.setMessageIds(payload.getMessageIds());
        messageReadService.markDelivered(Long.parseLong(principal.getName()), request);
    }
}
