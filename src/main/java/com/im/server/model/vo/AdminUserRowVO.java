package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserRowVO {

    private Long id;
    private String nickname;
    private String phoneMasked;
    private String emailMasked;
    private Integer status;
    private Integer admin;
    private LocalDateTime createdAt;
}
