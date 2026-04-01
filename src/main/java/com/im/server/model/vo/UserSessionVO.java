package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSessionVO {

    private Long sessionId;
    private String deviceId;
    private String deviceName;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private Boolean revoked;
}
