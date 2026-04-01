package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportMessageRequest {

    @NotNull(message = "消息不能为空")
    private Long messageId;

    @NotBlank(message = "举报原因不能为空")
    private String reason;

    private String remark;
}
