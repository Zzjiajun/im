package com.im.server.model.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class GroupMemberOperateRequest {

    @NotEmpty(message = "成员列表不能为空")
    private List<Long> memberIds;
}
