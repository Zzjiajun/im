package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_pinned_user")
public class MessagePinnedUser {

    private Long id;
    private Long userId;
    private Long messageId;
    private LocalDateTime createdAt;
}
