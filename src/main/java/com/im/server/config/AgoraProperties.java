package com.im.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Slf4j
@ConfigurationProperties(prefix = "app.agora")
public class AgoraProperties {

    private boolean enabled;
    private String appId;
    private String appCertificate;
    private int rtcTokenExpireSeconds = 3600;
    private int sessionTtlSeconds = 7200;
    private int ringTimeoutSeconds = 45;

    @PostConstruct
    public void logLoadedState() {
        log.info(
            "Agora config loaded: enabled={}, appIdConfigured={}, appCertificateConfigured={}",
            enabled,
            appId != null && !appId.isBlank(),
            appCertificate != null && !appCertificate.isBlank()
        );
    }
}
