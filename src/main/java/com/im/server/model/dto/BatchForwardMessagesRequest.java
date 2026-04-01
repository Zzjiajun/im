package com.im.server.model.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class BatchForwardMessagesRequest {

    @NotEmpty(message = "源消息列表不能为空")
    private List<Long> sourceMessageIds;

    @NotEmpty(message = "目标会话不能为空")
    private List<Long> targetConversationIds;
}
