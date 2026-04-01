package com.im.server.model.dto;

import lombok.Data;

@Data
public class ClearConversationRequest {

    private Long beforeMessageId;
}
