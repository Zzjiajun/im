package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinGroupInviteRequest {

    @NotBlank
    private String token;
}
