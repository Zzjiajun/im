package com.im.server.model.dto;

import com.im.server.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "会话不能为空")
    private Long conversationId;

    @NotNull(message = "消息类型不能为空")
    private MessageType type;

    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private Long replyMessageId;
    private Boolean mentionAll;
    private List<Long> mentionUserIds;
}
