package com.im.server.model.dto;

import com.im.server.model.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotNull
    private AuthType authType;

    @NotBlank
    private String account;

    @NotBlank
    private String verifyCode;

    @NotBlank
    private String newPassword;
}
