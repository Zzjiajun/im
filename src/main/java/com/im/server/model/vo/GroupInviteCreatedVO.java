package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupInviteCreatedVO {

    private String token;
    private LocalDateTime expireAt;
    private Integer maxUses;
}
