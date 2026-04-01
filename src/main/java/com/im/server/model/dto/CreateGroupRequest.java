package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "群名称不能为空")
    private String name;

    private String avatar;

    @NotEmpty(message = "群成员不能为空")
    private List<Long> memberIds;
}
