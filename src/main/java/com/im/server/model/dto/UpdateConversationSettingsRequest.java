package com.im.server.model.dto;

import lombok.Data;

@Data
public class UpdateConversationSettingsRequest {

    private Boolean pinned;
    private Boolean muted;
    private Boolean archived;
}
