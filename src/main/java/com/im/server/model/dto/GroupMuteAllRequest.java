package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupMuteAllRequest {

    @NotNull
    private Boolean muteAll;
}
