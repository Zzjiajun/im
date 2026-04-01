package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_blacklist")
public class UserBlacklist {

    private Long id;
    private Long userId;
    private Long blockedUserId;
    private LocalDateTime createdAt;
}
