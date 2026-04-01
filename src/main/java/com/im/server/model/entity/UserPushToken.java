package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_push_token")
public class UserPushToken {

    private Long id;
    private Long userId;
    private String platform;
    private String deviceToken;
    private LocalDateTime updatedAt;
}
