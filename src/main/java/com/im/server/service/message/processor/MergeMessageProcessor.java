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
public class MergeMessageProcessor implements MessageTypeProcessor {
    private final ObjectMapper objectMapper;

    public MergeMessageProcessor(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override public MessageType supportedType() { return MessageType.MERGE; }

    @Override
    public void validate(SendMessageRequest request) {
        validateJsonContent(request.getContent());
    }

    public void validateJsonContent(String content) {
        if (StringUtils.isBlank(content))
            throw new BusinessException("合并消息内容不能为空");
        try {
            JsonNode n = objectMapper.readTree(content);
            if (!n.hasNonNull("title") || !n.get("title").isTextual())
                throw new BusinessException("合并消息格式不正确");
            if (!n.has("items") || !n.get("items").isArray() || n.get("items").isEmpty())
                throw new BusinessException("合并消息格式不正确");
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException("合并消息内容格式不正确"); }
    }

    @Override public String buildPreview(ChatMessage message) { return "[聊天记录]"; }
}
