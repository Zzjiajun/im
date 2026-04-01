package com.im.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** 是否启用全局限流（需 Redis） */
    private boolean enabled = true;
    /** 普通 API：每 key（优先用户 id，否则 IP）每分钟上限 */
    private int generalPerMinute = 240;
    /** 登录/注册/发码/刷新等：每 IP 每分钟上限 */
    private int authIpPerMinute = 45;
    /** 消息搜索：每 key 每分钟上限 */
    private int searchPerMinute = 60;
}
