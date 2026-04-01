package com.im.server.model.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MuteGroupMemberRequest {

    @NotNull
    private Long userId;

    private LocalDateTime mutedUntil;
}
