package com.im.server.config;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    private boolean verifyOnRegister;
    private int codeTtlSeconds = 300;
    /** 发送验证码最小间隔（秒），防刷 */
    private int sendCodeMinIntervalSeconds = 60;
    /** 是否在日志中打印验证码（生产环境建议 false，依赖邮件/短信送达） */
    private boolean verifyCodeLog = true;
    /** 邮件发送验证码时的主题 */
    private String verifyEmailSubject = "IM 验证码";
    /**
     * true：手机号走 {@link com.im.server.service.sms.LoggingSmsVerifyCodeSender} 日志桩（默认）。
     * 配置 {@link #smsWebhook} 并启用后，将走 Webhook 发送，此时前端应视为非「仅日志」模式。
     */
    private boolean smsStubMode = true;
    /** 可选：HTTP 回调将验证码交给自建服务，再由其调用阿里云/腾讯云短信 */
    private SmsWebhookProperties smsWebhook = new SmsWebhookProperties();
    /** true：允许手机号注册/登录/发码；false 时仅允许邮箱（默认 false） */
    private boolean phoneAuthEnabled = false;

    /**
     * 给前端的「是否仅日志桩」提示：Webhook 真实发送时为 false。
     */
    public boolean isSmsStubModeForPublicApi() {
        if (smsWebhook != null && smsWebhook.isEnabled() && StringUtils.isNotBlank(smsWebhook.getUrl())) {
            return false;
        }
        return smsStubMode;
    }

    @Data
    public static class SmsWebhookProperties {
        private boolean enabled = false;
        /** POST 接收 JSON：phone, code, purpose, ttlSeconds */
        private String url;
        /** 可选：附加请求头，如 Authorization */
        private String headerName;
        private String headerValue;
    }
}
