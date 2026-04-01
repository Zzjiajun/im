package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterPushTokenRequest {

    @NotBlank
    private String platform;

    @NotBlank
    private String deviceToken;
}
