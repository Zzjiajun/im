package com.im.server.model.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSimpleVO {

    private Long userId;
    private String nickname;
    private String aliasName;
    private String avatar;
    private String phone;
    private String email;
    private List<Long> tagIds;
}
