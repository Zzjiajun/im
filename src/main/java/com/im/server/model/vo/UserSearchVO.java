package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Boolean friend;
}
