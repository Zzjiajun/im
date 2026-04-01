package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("friend_relation")
public class FriendRelation {

    private Long id;
    private Long userId;
    private Long friendUserId;
    private String aliasName;
    private LocalDateTime createdAt;
}
