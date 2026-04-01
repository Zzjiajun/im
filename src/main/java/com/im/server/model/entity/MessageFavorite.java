package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("message_favorite")
public class MessageFavorite {

    private Long id;
    private Long userId;
    private Long messageId;
    private String note;
    private String categoryName;
    private LocalDateTime createdAt;
}
