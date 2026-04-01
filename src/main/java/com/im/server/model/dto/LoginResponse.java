package com.im.server.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String nickname;
    /** 1 表示管理员，登录后台可用 */
    private Integer admin;
    private String token;
    private String refreshToken;
}
