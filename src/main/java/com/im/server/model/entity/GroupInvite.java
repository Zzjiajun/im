package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("group_invite")
public class GroupInvite {

    private Long id;
    private Long conversationId;
    private String token;
    private Long creatorId;
    private LocalDateTime expireAt;
    private Integer maxUses;
    private Integer usedCount;
    private LocalDateTime createdAt;
}
