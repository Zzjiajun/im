package com.im.server.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgoraRtcTokenVO {

    private String appId;
    private String channelName;
    private String uid;
    private String token;
    private Integer expiresInSeconds;
}
