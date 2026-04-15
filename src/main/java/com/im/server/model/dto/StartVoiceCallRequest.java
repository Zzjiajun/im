package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartVoiceCallRequest {

    @NotNull(message = "conversationId 不能为空")
    private Long conversationId;
}
