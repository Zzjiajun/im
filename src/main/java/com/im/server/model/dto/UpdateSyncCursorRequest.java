package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSyncCursorRequest {

    @NotNull
    private Long messageId;
}
