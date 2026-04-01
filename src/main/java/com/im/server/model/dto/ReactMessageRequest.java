package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReactMessageRequest {

    @NotNull(message = "消息不能为空")
    private Long messageId;

    @NotBlank(message = "反应类型不能为空")
    private String reactionType;
}
