package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendFriendRequestDTO {

    @NotNull(message = "目标用户不能为空")
    private Long toUserId;

    private String remark;
}
