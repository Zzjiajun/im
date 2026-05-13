package com.im.server.service.sms;

import com.im.server.common.BusinessException;
import com.im.server.config.AppAuthProperties;
import com.im.server.model.enums.VerifyCodePurpose;
import com.unimtx.Uni;
import com.unimtx.UniException;
import com.unimtx.model.UniMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 通过 Unimatrix Java SDK 发送短信验证码。
 * <p>
 * SDK 文档：https://github.com/unimtx/uni-java-sdk
 * <p>
 * 使用方式：初始化时传入 accessKeyId + accessKeySecret，
 * SDK 自动处理 HMAC 签名和请求发送。短信签名需在 Unimatrix 控制台提前申请。
 * 手机号自动转为 E.164 格式（+86 前缀）。
 */
@Slf4j
@RequiredArgsConstructor
public class UnimatrixSmsVerifyCodeSender implements SmsVerifyCodeSender {

    private final AppAuthProperties.UnimatrixSmsProperties props;

    @PostConstruct
    public void init() {
        String accessKeyId = StringUtils.trimToNull(props.getAccessKeyId());
        String accessKeySecret = StringUtils.trimToNull(props.getAccessKeySecret());
        if (accessKeyId != null && accessKeySecret != null) {
            Uni.init(accessKeyId, accessKeySecret);
            log.info("[Unimatrix] SDK 初始化完成");
        }
    }

    @Override
    public void send(String phone, VerifyCodePurpose purpose, String code, int ttlSeconds) {
        String accessKeyId = StringUtils.trimToNull(props.getAccessKeyId());
        String accessKeySecret = StringUtils.trimToNull(props.getAccessKeySecret());
        if (accessKeyId == null || accessKeySecret == null) {
            log.warn("[Unimatrix] accessKeyId 或 accessKeySecret 未配置，跳过发送 phone={}", phone);
            return;
        }

        // 确保 SDK 已初始化
        Uni.init(accessKeyId, accessKeySecret);

        String to = formatPhone(phone);
        String contentTemplate = StringUtils.defaultString(props.getContentTemplate(),
            "您的验证码是{code}，{ttlMinutes}分钟内有效。");
        String text = contentTemplate
            .replace("{code}", code)
            .replace("{ttlMinutes}", String.valueOf(Math.max(1, ttlSeconds / 60)));

        try {
            UniMessage message = UniMessage.build()
                .setTo(to)
                .setText(text);
            message.send();
            log.info("[Unimatrix] 短信已投递 phone={} purpose={}", phone, purpose);
        } catch (UniException e) {
            log.error("[Unimatrix] 发送失败 phone={} requestId={}: {}", phone, e.requestId, e.getMessage());
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }

    private static String formatPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() > 11) {
            return "+" + digits;
        }
        if (digits.startsWith("+")) {
            return digits;
        }
        return "+86" + digits;
    }
}
