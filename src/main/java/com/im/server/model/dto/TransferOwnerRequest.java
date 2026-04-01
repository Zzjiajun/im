package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferOwnerRequest {

    @NotNull(message = "目标成员不能为空")
    private Long targetUserId;
}
