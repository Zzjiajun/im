package com.im.server.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 JSON 中的 Long 序列化为字符串，避免前端 JavaScript 超过 Number.MAX_SAFE_INTEGER 时精度丢失
 * （如 MyBatis-Plus 雪花 ID）。
 */
@Configuration
public class JacksonLongAsStringConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonLongAsStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
