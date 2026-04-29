package com.im.server.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 配置标记 Bean。
 * 仅当 spring.elasticsearch.uris 有值时加载（与 Spring Boot 自动配置条件一致），
 * 关闭时降级为 MySQL LIKE 搜索。
 * 其他 ES 服务类通过 @ConditionalOnBean(ElasticsearchConfig.class) 仅在启用时加载。
 */
@Getter
@Configuration
@ConditionalOnProperty(prefix = "spring.elasticsearch", name = "uris")
public class ElasticsearchConfig {

    /** ES 索引名，引用自 app.elasticsearch.index */
    @Value("${app.elasticsearch.index:im_messages_v1}")
    private String index;
}
