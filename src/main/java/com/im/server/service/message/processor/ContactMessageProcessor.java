package com.im.server.service.message.processor;

import com.im.server.common.BusinessException;
import com.im.server.model.dto.SendMessageRequest;
import com.im.server.model.entity.ChatMessage;
import com.im.server.model.enums.MessageType;
import com.im.server.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ContactMessageProcessor implements MessageTypeProcessor {
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public ContactMessageProcessor(ObjectMapper objectMapper, UserService userService) {
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override public MessageType supportedType() { return MessageType.CONTACT; }

    @Override
    public void validate(SendMessageRequest request) {
        if (StringUtils.isBlank(request.getContent()))
            throw new BusinessException("名片消息内容不能为空");
        try {
            JsonNode n = objectMapper.readTree(request.getContent());
            if (!n.has("userId"))
                throw new BusinessException("名片消息格式不正确");
            userService.getSimpleUser(n.get("userId").asLong());
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("名片消息内容格式不正确"); }
    }

    @Override public String buildPreview(ChatMessage message) { return "[名片]"; }
}
