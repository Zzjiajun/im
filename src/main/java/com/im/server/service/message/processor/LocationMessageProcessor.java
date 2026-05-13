package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class LocationMessageProcessor implements MessageTypeProcessor {
    private final ObjectMapper objectMapper;

    public LocationMessageProcessor(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public MessageType supportedType() { return MessageType.LOCATION; }

    @Override
    public void validate(SendMessageRequest request) {
        if (StringUtils.isBlank(request.getContent()))
            throw new BusinessException("位置消息内容不能为空");
        try {
            JsonNode n = objectMapper.readTree(request.getContent());
            if (!n.has("lat") || !n.has("lng"))
                throw new BusinessException("位置消息格式不正确");
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("位置消息内容格式不正确"); }
    }

    @Override public String buildPreview(ChatMessage message) { return "[位置]"; }
}
