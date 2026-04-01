package com.im.server.service.sms;

import com.im.server.common.BusinessException;
import com.im.server.config.AppAuthProperties;
import com.im.server.model.enums.VerifyCodePurpose;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * 将验证码 POST 到自建 Webhook，由对方调用阿里云/腾讯云等短信网关。
 */
@Slf4j
@RequiredArgsConstructor
public class WebhookSmsVerifyCodeSender implements SmsVerifyCodeSender {

    private final RestTemplate restTemplate;
    private final AppAuthProperties.SmsWebhookProperties props;

    @Override
    public void send(String phone, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        String url = props.getUrl();
        if (StringUtils.isBlank(url)) {
            log.warn("[SMS Webhook] url 未配置，跳过发送 phone={}", phone);
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(props.getHeaderName()) && StringUtils.isNotBlank(props.getHeaderValue())) {
            headers.add(props.getHeaderName(), props.getHeaderValue());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("phone", phone);
        body.put("code", code);
        body.put("purpose", purpose.name());
        body.put("ttlSeconds", ttlSeconds);
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            log.info("[SMS Webhook] 已投递 phone={} purpose={}", phone, purpose);
        } catch (Exception e) {
            log.error("[SMS Webhook] 调用失败 url={} phone={}: {}", url, phone, e.getMessage());
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }
}
