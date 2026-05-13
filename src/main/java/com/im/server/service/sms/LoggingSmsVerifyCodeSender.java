package com.im.server.service.sms;

import com.im.server.model.enums.VerifyCodePurpose;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class LoggingSmsVerifyCodeSender implements SmsVerifyCodeSender {

    @Override
    public void send(String phone, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        log.debug(
            "[SMS 预留] 未接入真实短信网关。phone={} purpose={} code={} ttlSeconds={} — 实现 {} 并注册为 @Primary 可对接阿里云/腾讯云",
            maskPhone(phone), purpose, maskCode(code), ttlSeconds, SmsVerifyCodeSender.class.getSimpleName());
    }

    private static String maskPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 7) {
            return StringUtils.left(trimmed, 2) + "***";
        }
        return StringUtils.left(trimmed, 3) + "****" + StringUtils.right(trimmed, 4);
    }

    private static String maskCode(String code) {
        if (StringUtils.isBlank(code)) {
            return "";
        }
        return "***" + StringUtils.right(code, 2);
    }
}
