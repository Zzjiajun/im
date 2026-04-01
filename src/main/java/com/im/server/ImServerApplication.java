package com.im.server;

import com.im.server.config.AppAuthProperties;
import com.im.server.config.PushProperties;
import com.im.server.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppAuthProperties.class, PushProperties.class, RateLimitProperties.class})
public class ImServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImServerApplication.class, args);
    }
}
