package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaMetaVO {

    private String mediaType;
    private String originalName;
    private String contentType;
    private Long size;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private String coverUrl;
}
