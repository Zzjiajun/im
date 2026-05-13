package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 文本消息处理器。
 */
@Component
public class TextMessageProcessor implements MessageTypeProcessor {

    @Override
    public MessageType supportedType() {
        return MessageType.TEXT;
    }

    @Override
    public void validate(SendMessageRequest request) {
        if (StringUtils.isBlank(request.getContent())) {
            throw new BusinessException("文本消息内容不能为空");
        }
    }

    @Override
    public String buildPreview(ChatMessage message) {
        return StringUtils.abbreviate(message.getContent(), 50);
    }
}
