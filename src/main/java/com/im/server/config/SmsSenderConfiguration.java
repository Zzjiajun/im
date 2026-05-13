package com.im.server.config;

//import com.im.server.service.sms.AliyunSmsVerifyCodeSender;
import com.im.server.service.sms.LoggingSmsVerifyCodeSender;
import com.im.server.service.sms.SmsVerifyCodeSender;
import com.im.server.service.sms.UnimatrixSmsVerifyCodeSender;
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
        // 1) Unimatrix SDK 直连短信
        AppAuthProperties.UnimatrixSmsProperties u = appAuthProperties.getUnimatrixSms();
        if (u != null && u.isEnabled() && StringUtils.isNotBlank(u.getAccessKeyId())) {
            return new UnimatrixSmsVerifyCodeSender(u);
        }
//        // 2) 阿里云短信
//        AppAuthProperties.AliyunSmsProperties a = appAuthProperties.getAliyunSms();
//        if (a != null && a.isEnabled() && StringUtils.isNotBlank(a.getAccessKeyId())) {
//            return new AliyunSmsVerifyCodeSender(a);
//        }
        // 3) Webhook 回调
        AppAuthProperties.SmsWebhookProperties w = appAuthProperties.getSmsWebhook();
        if (w != null && w.isEnabled() && StringUtils.isNotBlank(w.getUrl())) {
            return new WebhookSmsVerifyCodeSender(restTemplateBuilder.build(), w);
        }
        // 3) 日志桩
        return new LoggingSmsVerifyCodeSender();
    }
}
