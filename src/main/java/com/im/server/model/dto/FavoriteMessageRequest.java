package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FavoriteMessageRequest {

    @NotNull(message = "消息不能为空")
    private Long messageId;

    private String note;
    private String categoryName;
}
