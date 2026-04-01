package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("im_user")
public class User {

    private Long id;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private String password;
    private Integer status;
    private Integer admin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
