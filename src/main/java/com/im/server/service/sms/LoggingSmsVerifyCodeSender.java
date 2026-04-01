package com.im.server.service.sms;

import com.im.server.model.enums.VerifyCodePurpose;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingSmsVerifyCodeSender implements SmsVerifyCodeSender {

    @Override
    public void send(String phone, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        log.info(
            "[SMS 预留] 未接入真实短信网关。phone={} purpose={} code={} ttlSeconds={} — 实现 {} 并注册为 @Primary 可对接阿里云/腾讯云",
            phone, purpose, code, ttlSeconds, SmsVerifyCodeSender.class.getSimpleName());
    }
}
