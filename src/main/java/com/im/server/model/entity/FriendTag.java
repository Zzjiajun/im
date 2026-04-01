package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("friend_tag")
public class FriendTag {

    private Long id;
    private Long userId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
