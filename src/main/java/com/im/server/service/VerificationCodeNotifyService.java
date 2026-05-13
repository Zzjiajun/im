package com.im.server.service;

import com.im.server.config.AppAuthProperties;
import com.im.server.model.enums.AuthType;
import com.im.server.model.enums.VerifyCodePurpose;
import com.im.server.service.sms.SmsVerifyCodeSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeNotifyService {

    private final AppAuthProperties appAuthProperties;
    private final SmsVerifyCodeSender smsVerifyCodeSender;

    @Autowired(required = false)
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public void notifyAccount(AuthType authType, String account, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        if (appAuthProperties.isVerifyCodeLog()) {
            log.debug("[VerifyCode] purpose={} type={} account={} code={} (ttl={}s)",
                purpose, authType, maskAccount(account), maskCode(code), ttlSeconds);
        }
        if (authType == AuthType.EMAIL) {
            trySendEmail(account, purpose, code, ttlSeconds);
        } else {
            smsVerifyCodeSender.send(account, purpose, code, ttlSeconds);
        }
    }

    public boolean isEmailDeliveryAvailable() {
        return javaMailSender != null && StringUtils.isNotBlank(mailUsername);
    }

    private void trySendEmail(String to, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        if (javaMailSender == null) {
            log.debug("未配置可用 JavaMailSender（检查 spring.mail.host），跳过邮件。to={}", to);
            return;
        }
        if (StringUtils.isBlank(mailUsername)) {
            log.warn("spring.mail.username 未设置，无法发件，跳过邮件 to={}", to);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(mailUsername);
        msg.setSubject(appAuthProperties.getVerifyEmailSubject());
        msg.setText(String.format(
            "您的验证码为：%s\n用途：%s\n%d 分钟内有效。\n如非本人操作请忽略。",
            code,
            purpose.name(),
            Math.max(1, ttlSeconds / 60)));
        try {
            javaMailSender.send(msg);
            log.info("验证码邮件已发送至 {}", to);
        } catch (Exception e) {
            log.error("发送验证码邮件失败 to={}: {}", to, e.getMessage());
        }
    }

    private static String maskAccount(String account) {
        if (StringUtils.isBlank(account)) {
            return "";
        }
        String trimmed = account.trim();
        int at = trimmed.indexOf('@');
        if (at > 0) {
            String local = trimmed.substring(0, at);
            String domain = trimmed.substring(at);
            return StringUtils.left(local, 2) + "***" + domain;
        }
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
