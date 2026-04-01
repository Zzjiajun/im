package com.im.server.service.sms;

import com.im.server.model.enums.VerifyCodePurpose;

/**
 * 手机号短信验证码发送。默认实现为日志桩；对接阿里云/腾讯云短信时提供同名 {@code @Primary} Bean 即可替换。
 */
public interface SmsVerifyCodeSender {

    void send(String phone, VerifyCodePurpose purpose, String code, int ttlSeconds);
}
