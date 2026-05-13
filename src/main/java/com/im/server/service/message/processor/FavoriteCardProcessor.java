package com.im.server.service.message.processor;

import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import org.springframework.stereotype.Component;

@Component
public class FavoriteCardProcessor implements MessageTypeProcessor {
    @Override public MessageType supportedType() { return MessageType.FAVORITE_CARD; }
    @Override public String buildPreview(ChatMessage message) { return "[收藏]"; }
}
