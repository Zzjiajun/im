package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.config.AppAuthProperties;
import com.im.server.mapper.UserMapper;
import com.im.server.mapper.UserOauthBindingMapper;
import com.im.server.model.dto.LoginRequest;
import com.im.server.model.dto.LoginResponse;
import com.im.server.model.dto.OAuthLoginRequest;
import com.im.server.model.dto.RegisterRequest;
import com.im.server.model.dto.ResetPasswordRequest;
import com.im.server.model.dto.SendVerifyCodeRequest;
import com.im.server.model.entity.User;
import com.im.server.model.entity.UserOauthBinding;
import com.im.server.model.enums.AuthType;
import com.im.server.model.enums.VerifyCodePurpose;
import com.im.server.security.JwtTokenProvider;
import com.im.server.security.LoginUser;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserOauthBindingMapper userOauthBindingMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionService userSessionService;
    private final VerificationCodeService verificationCodeService;
    private final AppAuthProperties appAuthProperties;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        assertPhoneAuthAllowed(request.getAuthType());
        if (appAuthProperties.isVerifyOnRegister()) {
            verificationCodeService.assertCode(
                request.getAuthType(), request.getAccount(), VerifyCodePurpose.REGISTER, request.getVerifyCode());
        } else if (StringUtils.isNotBlank(request.getVerifyCode())) {
            verificationCodeService.assertCode(
                request.getAuthType(), request.getAccount(), VerifyCodePurpose.REGISTER, request.getVerifyCode());
        }
        User existed = getByAccount(request.getAuthType(), request.getAccount());
        if (existed != null) {
            throw new BusinessException("账号已存在");
        }
        String nickname = StringUtils.trim(request.getNickname());
        if (nickname.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        long nickDup = userMapper.selectCount(
            new LambdaQueryWrapper<User>().eq(User::getNickname, nickname));
        if (nickDup > 0) {
            throw new BusinessException("昵称已被占用");
        }
        User user = new User();
        user.setNickname(nickname);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setAdmin(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        if (request.getAuthType() == AuthType.PHONE) {
            user.setPhone(request.getAccount());
        } else {
            user.setEmail(request.getAccount());
        }
        userMapper.insert(user);
        UserSessionService.SessionTokens session = userSessionService.createSession(user.getId(), null, null);
        return buildLoginResponse(user, session.refreshTokenPlain());
    }

    public LoginResponse login(LoginRequest request) {
        User user = getByAccount(request.getAuthType(), request.getAccount());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        assertAccountActive(user);
        UserSessionService.SessionTokens session =
            userSessionService.createSession(user.getId(), request.getDeviceId(), request.getDeviceName());
        return buildLoginResponse(user, session.refreshTokenPlain());
    }

    @Transactional
    public LoginResponse oauthLogin(OAuthLoginRequest request) {
        String provider = StringUtils.upperCase(StringUtils.trimToEmpty(request.getProvider()));
        String openId = StringUtils.trimToEmpty(request.getOpenId());
        if (provider.isEmpty() || openId.isEmpty()) {
            throw new BusinessException("provider 与 openId 不能为空");
        }
        UserOauthBinding binding = userOauthBindingMapper.selectOne(
            new LambdaQueryWrapper<UserOauthBinding>()
                .eq(UserOauthBinding::getProvider, provider)
                .eq(UserOauthBinding::getOpenId, openId)
        );
        User user;
        if (binding != null) {
            user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw new BusinessException("绑定数据异常");
            }
            assertAccountActive(user);
        } else {
            user = new User();
            String nickBase = StringUtils.defaultIfBlank(
                request.getNickname(),
                "u_" + openId.substring(0, Math.min(8, openId.length())));
            user.setNickname(pickUniqueOAuthNickname(nickBase));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setStatus(1);
            user.setAdmin(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            UserOauthBinding b = new UserOauthBinding();
            b.setUserId(user.getId());
            b.setProvider(provider);
            b.setOpenId(openId);
            b.setCreatedAt(LocalDateTime.now());
            userOauthBindingMapper.insert(b);
        }
        UserSessionService.SessionTokens session =
            userSessionService.createSession(user.getId(), request.getDeviceId(), request.getDeviceName());
        return buildLoginResponse(user, session.refreshTokenPlain());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        assertPhoneAuthAllowed(request.getAuthType());
        verificationCodeService.assertCode(
            request.getAuthType(), request.getAccount(), VerifyCodePurpose.RESET_PASSWORD, request.getVerifyCode());
        User user = getByAccount(request.getAuthType(), request.getAccount());
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    public void sendVerifyCode(SendVerifyCodeRequest request) {
        assertPhoneAuthAllowed(request.getAuthType());
        String account = StringUtils.trimToEmpty(request.getAccount());
        if (request.getPurpose() == VerifyCodePurpose.REGISTER) {
            User existed = getByAccount(request.getAuthType(), account);
            if (existed != null) {
                throw new BusinessException("该账号已注册，请直接登录");
            }
        }
        if (request.getPurpose() == VerifyCodePurpose.RESET_PASSWORD) {
            User u = getByAccount(request.getAuthType(), account);
            if (u == null) {
                throw new BusinessException("账号不存在");
            }
        }
        verificationCodeService.sendCode(request.getAuthType(), account, request.getPurpose());
    }

    private void assertPhoneAuthAllowed(AuthType authType) {
        if (authType == AuthType.PHONE && !appAuthProperties.isPhoneAuthEnabled()) {
            throw new BusinessException("当前仅支持邮箱，请使用邮箱注册或登录");
        }
    }

    private String pickUniqueOAuthNickname(String base) {
        String n = StringUtils.trimToEmpty(base);
        if (n.length() > 50) {
            n = n.substring(0, 50);
        }
        if (n.isEmpty()) {
            n = "user";
        }
        String candidate = n;
        int suffix = 0;
        while (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getNickname, candidate)) > 0) {
            suffix++;
            String suf = "_" + suffix;
            int maxBase = Math.max(1, 64 - suf.length());
            candidate = (n.length() > maxBase ? n.substring(0, maxBase) : n) + suf;
        }
        return candidate;
    }

    private User getByAccount(AuthType authType, String account) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (authType == AuthType.PHONE) {
            wrapper.eq(User::getPhone, account);
        } else {
            wrapper.eq(User::getEmail, account);
        }
        return userMapper.selectOne(wrapper);
    }

    private void assertAccountActive(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已禁用");
        }
    }

    private LoginResponse buildLoginResponse(User user, String refreshTokenPlain) {
        boolean admin = user.getAdmin() != null && user.getAdmin() == 1;
        String token = jwtTokenProvider.createToken(new LoginUser(user.getId(), user.getNickname(), admin));
        return LoginResponse.builder()
            .userId(user.getId())
            .nickname(user.getNickname())
            .admin(user.getAdmin())
            .token(token)
            .refreshToken(refreshTokenPlain)
            .build();
    }
}
