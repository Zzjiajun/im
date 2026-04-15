package com.im.server.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    /** 多实例时启用 RabbitMQ STOMP 插件中继（见 README） */
    private Relay relay = new Relay();
    /** STOMP 入站限流（需 Redis） */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Relay {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 61613;
        private String clientLogin = "guest";
        private String clientPasscode = "guest";
        private String systemLogin = "guest";
        private String systemPasscode = "guest";
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int sendPerMinute = 120;
        private int typingPerMinute = 90;
        private int deliverPerMinute = 180;
    }
}
