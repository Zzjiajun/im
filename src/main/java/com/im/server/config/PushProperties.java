package com.im.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 离线推送扩展：可配置 Webhook，由自建服务对接 FCM/APNs/厂商通道。
 */
@Data
@ConfigurationProperties(prefix = "app.push")
public class PushProperties {

    /** 配置后对新消息离线用户 POST JSON（见 OfflinePushService） */
    private String webhookUrl;
    private String webhookHeaderName;
    private String webhookHeaderValue;
}
