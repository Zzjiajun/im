package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("friend_tag_member")
public class FriendTagMember {

    private Long id;
    private Long tagId;
    private Long friendUserId;
    private LocalDateTime createdAt;
}
