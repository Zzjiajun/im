package com.im.server.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ForwardMessageRequest {

    @NotNull(message = "源消息不能为空")
    private Long sourceMessageId;

    @NotEmpty(message = "目标会话不能为空")
    private List<Long> targetConversationIds;
}
