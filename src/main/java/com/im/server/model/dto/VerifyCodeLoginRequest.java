package com.im.server.model.dto;

import com.im.server.model.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyCodeLoginRequest {

    @NotNull(message = "登录类型不能为空")
    private AuthType authType;

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    private String deviceId;

    private String deviceName;
}
