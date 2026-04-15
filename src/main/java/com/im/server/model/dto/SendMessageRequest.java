package com.im.server.model.dto;

import com.im.server.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "会话不能为空")
    private Long conversationId;

    @NotNull(message = "消息类型不能为空")
    private MessageType type;

    /** 可选；同一发送者下唯一，用于重试去重 */
    @Size(max = 64, message = "clientMsgId 过长")
    private String clientMsgId;

    private String content;
    private String mediaUrl;
    private String mediaCoverUrl;
    private Long replyMessageId;
    private Boolean mentionAll;
    private List<Long> mentionUserIds;
}
