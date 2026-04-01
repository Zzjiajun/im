package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandleFriendRequestDTO {

    @NotNull(message = "申请单不能为空")
    private Long requestId;

    @NotNull(message = "处理结果不能为空")
    private Boolean accept;
}
