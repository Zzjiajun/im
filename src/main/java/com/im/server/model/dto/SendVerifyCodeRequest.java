package com.im.server.model.dto;

import com.im.server.model.enums.AuthType;
import com.im.server.model.enums.VerifyCodePurpose;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendVerifyCodeRequest {

    @NotNull
    private AuthType authType;

    @NotNull
    private String account;

    @NotNull
    private VerifyCodePurpose purpose;
}
