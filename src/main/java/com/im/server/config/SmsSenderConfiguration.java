package com.im.server.config;

import com.im.server.service.sms.LoggingSmsVerifyCodeSender;
import com.im.server.service.sms.SmsVerifyCodeSender;
import com.im.server.service.sms.WebhookSmsVerifyCodeSender;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SmsSenderConfiguration {

    private final AppAuthProperties appAuthProperties;

    @Bean
    public SmsVerifyCodeSender smsVerifyCodeSender(RestTemplateBuilder restTemplateBuilder) {
        AppAuthProperties.SmsWebhookProperties w = appAuthProperties.getSmsWebhook();
        if (w != null && w.isEnabled() && StringUtils.isNotBlank(w.getUrl())) {
            return new WebhookSmsVerifyCodeSender(restTemplateBuilder.build(), w);
        }
        return new LoggingSmsVerifyCodeSender();
    }
}
