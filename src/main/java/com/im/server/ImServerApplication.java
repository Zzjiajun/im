package com.im.server;

import com.im.server.config.AppAuthProperties;
import com.im.server.config.PushProperties;
import com.im.server.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(
        basePackages = "com.im.server",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.linkkou\\..*")
)
@EnableConfigurationProperties({AppAuthProperties.class, PushProperties.class, RateLimitProperties.class})
public class ImServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImServerApplication.class, args);
    }
}
