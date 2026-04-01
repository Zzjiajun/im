package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_session")
public class UserSession {

    private Long id;
    private Long userId;
    private String refreshTokenHash;
    private String deviceId;
    private String deviceName;
    private Integer revoked;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
}
