import { http, unwrap } from './http'
import type {
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  OAuthLoginRequest,
  PublicAuthConfig,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  SendVerifyCodeRequest,
  UpdateProfileRequest,
  SnowflakeId,
  User,
  UserSessionVO,
} from '@/types/api'

export function fetchPublicConfig() {
  return unwrap<PublicAuthConfig>(http.get('/auth/public-config'))
}

export function login(body: LoginRequest) {
  return unwrap(http.post('/auth/login', body))
}

export function register(body: RegisterRequest) {
  return unwrap(http.post('/auth/register', body))
}

export function sendVerifyCode(body: SendVerifyCodeRequest) {
  return unwrap<void>(http.post('/auth/send-code', body))
}

export function resetPassword(body: ResetPasswordRequest) {
  return unwrap<void>(http.post('/auth/reset-password', body))
}

export function refresh(body: RefreshTokenRequest) {
  return unwrap<LoginResponse>(http.post('/auth/refresh', body))
}

export function oauthLogin(body: OAuthLoginRequest) {
  return unwrap<LoginResponse>(http.post('/auth/oauth/login', body))
}

export function logout(body?: LogoutRequest) {
  return unwrap<void>(http.post('/auth/logout', body ?? {}))
}

export function logoutAll() {
  return unwrap<void>(http.post('/auth/logout-all'))
}

export function fetchSessions() {
  return unwrap<UserSessionVO[]>(http.get('/auth/sessions'))
}

export function revokeSession(sessionId: SnowflakeId) {
  return unwrap<void>(http.delete(`/auth/sessions/${sessionId}`))
}

export function fetchMe() {
  return unwrap<User>(http.get('/auth/me'))
}

export function updateProfile(body: UpdateProfileRequest) {
  return unwrap<User>(http.post('/auth/profile', body))
}
