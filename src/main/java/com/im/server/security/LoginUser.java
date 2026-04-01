package com.im.server.security;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private boolean admin;

    public LoginUser(Long userId, String username) {
        this.userId = userId;
        this.username = username;
        this.admin = false;
    }
}
