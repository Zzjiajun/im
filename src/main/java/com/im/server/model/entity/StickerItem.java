package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sticker_item")
public class StickerItem {

    private Long id;
    private Long packId;
    private String code;
    private String imageUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
