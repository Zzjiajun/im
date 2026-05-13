package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import org.springframework.stereotype.Component;

/**
 * 系统消息处理器（仅限服务端使用，客户端不可发送）。
 */
@Component
public class SystemMessageProcessor implements MessageTypeProcessor {

    @Override
    public MessageType supportedType() {
        return MessageType.SYSTEM;
    }

    @Override
    public void validate(SendMessageRequest request) {
        throw new BusinessException("不能发送系统消息");
    }

    @Override
    public String buildPreview(ChatMessage message) {
        return "[系统消息]";
    }
}
