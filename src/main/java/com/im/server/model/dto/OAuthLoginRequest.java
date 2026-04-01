package com.im.server.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthLoginRequest {

    @NotBlank
    private String provider;

    @NotBlank
    private String openId;

    private String nickname;

    private String deviceId;

    private String deviceName;
}
