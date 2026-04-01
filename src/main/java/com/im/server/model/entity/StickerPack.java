package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sticker_pack")
public class StickerPack {

    private Long id;
    private String code;
    private String name;
    private String coverUrl;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
}
