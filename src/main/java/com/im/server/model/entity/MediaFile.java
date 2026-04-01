package com.im.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("media_file")
public class MediaFile {

    private Long id;
    private Long userId;
    private String mediaType;
    private String bucket;
    private String objectName;
    private String originalName;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private String coverUrl;
    private String url;
    private LocalDateTime createdAt;
}
