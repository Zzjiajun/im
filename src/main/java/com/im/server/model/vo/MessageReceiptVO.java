package com.im.server.model.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageReceiptVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private LocalDateTime actionAt;
}
