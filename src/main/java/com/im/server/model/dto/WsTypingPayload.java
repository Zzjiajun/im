package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WsTypingPayload {

    @NotNull
    private Long conversationId;

    @NotNull
    private Boolean typing;
}
