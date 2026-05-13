package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class FileMessageProcessor implements MessageTypeProcessor {
    @Override public MessageType supportedType() { return MessageType.FILE; }
    @Override public void validate(SendMessageRequest request) {
        if (StringUtils.isBlank(request.getMediaUrl()))
            throw new BusinessException("文件地址不能为空");
    }
    @Override public String buildPreview(ChatMessage message) { return "[文件]"; }
}
