package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupMemberVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private String role;
    private Boolean online;
    private Boolean blockedByMe;
    private Boolean hasBlockedMe;
}
