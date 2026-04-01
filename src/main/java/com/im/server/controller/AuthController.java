package com.im.server.controller;

import com.im.server.common.ApiResponse;
import com.im.server.common.CurrentUser;
import com.im.server.model.dto.LoginRequest;
import com.im.server.model.dto.LoginResponse;
import com.im.server.model.dto.LogoutRequest;
import com.im.server.model.dto.OAuthLoginRequest;
import com.im.server.model.dto.RefreshTokenRequest;
import com.im.server.model.dto.RegisterRequest;
import com.im.server.model.dto.ResetPasswordRequest;
import com.im.server.model.dto.SendVerifyCodeRequest;
import com.im.server.model.dto.UpdateProfileRequest;
import com.im.server.model.entity.User;
import com.im.server.config.AppAuthProperties;
import com.im.server.model.vo.PublicAuthConfigVO;
import com.im.server.model.vo.UserSessionVO;
import com.im.server.security.LoginUser;
import com.im.server.service.AuthService;
import com.im.server.service.UserService;
import com.im.server.service.UserSessionService;
import com.im.server.service.VerificationCodeNotifyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final AppAuthProperties appAuthProperties;
    private final VerificationCodeNotifyService verificationCodeNotifyService;

    @GetMapping("/public-config")
    public ApiResponse<PublicAuthConfigVO> publicConfig() {
        return ApiResponse.success(PublicAuthConfigVO.builder()
            .verifyOnRegister(appAuthProperties.isVerifyOnRegister())
            .emailDeliveryAvailable(verificationCodeNotifyService.isEmailDeliveryAvailable())
            .smsStubMode(appAuthProperties.isSmsStubModeForPublicApi())
            .phoneAuthEnabled(appAuthProperties.isPhoneAuthEnabled())
            .build());
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendVerifyCodeRequest request) {
        authService.sendVerifyCode(request);
        return ApiResponse.success("验证码已发送", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("密码已重置", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        UserSessionService.SessionTokens rotated = userSessionService.refreshAccess(request.getRefreshToken());
        String access = userSessionService.accessTokenForUserId(rotated.userId());
        User user = userService.getUser(rotated.userId());
        return ApiResponse.success(LoginResponse.builder()
            .userId(user.getId())
            .nickname(user.getNickname())
            .token(access)
            .refreshToken(rotated.refreshTokenPlain())
            .build());
    }

    @PostMapping("/oauth/login")
    public ApiResponse<LoginResponse> oauthLogin(@Valid @RequestBody OAuthLoginRequest request) {
        return ApiResponse.success(authService.oauthLogin(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            userSessionService.revokeByRefreshToken(request.getRefreshToken());
        }
        return ApiResponse.success("已登出", null);
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@CurrentUser LoginUser loginUser) {
        userSessionService.revokeAllSessions(loginUser.getUserId());
        return ApiResponse.success("已登出全部设备", null);
    }

    @GetMapping("/sessions")
    public ApiResponse<List<UserSessionVO>> sessions(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(userSessionService.listSessions(loginUser.getUserId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@CurrentUser LoginUser loginUser,
                                         @PathVariable Long sessionId) {
        userSessionService.revokeSession(loginUser.getUserId(), sessionId);
        return ApiResponse.success("会话已失效", null);
    }

    @GetMapping("/me")
    public ApiResponse<User> me(@CurrentUser LoginUser loginUser) {
        return ApiResponse.success(userService.getUser(loginUser.getUserId()));
    }

    @PostMapping("/profile")
    public ApiResponse<User> updateProfile(@CurrentUser LoginUser loginUser,
                                           @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(loginUser.getUserId(), request));
    }
}
