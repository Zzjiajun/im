package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MarkReadRequest {

    @NotNull(message = "会话不能为空")
    private Long conversationId;

    private Long lastReadMessageId;
}
