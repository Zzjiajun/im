package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class EditMessageRequest {

    @NotNull(message = "消息不能为空")
    private Long messageId;

    @NotBlank(message = "新内容不能为空")
    private String content;

    private Boolean mentionAll;
    private List<Long> mentionUserIds;
}
