package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PinMessageRequest {

    @NotNull(message = "消息不能为空")
    private Long messageId;
}
