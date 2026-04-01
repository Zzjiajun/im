package com.im.server.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private List<String> allowedOrigins = new ArrayList<>();
}
