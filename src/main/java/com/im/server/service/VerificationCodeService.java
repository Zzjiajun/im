package com.im.server.service;

import com.im.server.common.BusinessException;
import com.im.server.config.AppAuthProperties;
import com.im.server.model.enums.AuthType;
import com.im.server.model.enums.VerifyCodePurpose;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final AppAuthProperties appAuthProperties;
    private final VerificationCodeNotifyService verificationCodeNotifyService;

    public void sendCode(AuthType authType, String account, VerifyCodePurpose purpose) {
        String normalized = StringUtils.trimToEmpty(account);
        int interval = Math.max(30, appAuthProperties.getSendCodeMinIntervalSeconds());
        String rateKey = "im:send:rate:" + purpose.name() + ":" + authType.name() + ":" + normalized;
        Boolean firstInWindow = stringRedisTemplate.opsForValue()
            .setIfAbsent(rateKey, "1", Duration.ofSeconds(interval));
        if (Boolean.FALSE.equals(firstInWindow)) {
            throw new BusinessException("发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = redisKey(purpose, authType, normalized);
        int ttl = Math.max(60, appAuthProperties.getCodeTtlSeconds());
        stringRedisTemplate.opsForValue().set(key, code, ttl, TimeUnit.SECONDS);
        try {
            verificationCodeNotifyService.notifyAccount(authType, normalized, purpose, code, ttl);
        } catch (RuntimeException e) {
            stringRedisTemplate.delete(rateKey);
            stringRedisTemplate.delete(key);
            throw e;
        }
    }

    public void assertCode(AuthType authType, String account, VerifyCodePurpose purpose, String verifyCode) {
        if (StringUtils.isBlank(verifyCode)) {
            throw new BusinessException("验证码不能为空");
        }
        String key = redisKey(purpose, authType, StringUtils.trimToEmpty(account));
        String expected = stringRedisTemplate.opsForValue().get(key);
        if (expected == null || !expected.equals(StringUtils.trimToEmpty(verifyCode))) {
            throw new BusinessException("验证码无效或已过期");
        }
        stringRedisTemplate.delete(key);
    }

    private String redisKey(VerifyCodePurpose purpose, AuthType authType, String account) {
        return "im:verify:" + purpose.name() + ":" + authType.name() + ":" + account;
    }
}
