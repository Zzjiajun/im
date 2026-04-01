package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_oauth_binding")
public class UserOauthBinding {

    private Long id;
    private Long userId;
    private String provider;
    private String openId;
    private LocalDateTime createdAt;
}
