package com.im.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(LoginUser loginUser) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + jwtProperties.getAccessTokenExpireSeconds() * 1000);
        return Jwts.builder()
            .subject(String.valueOf(loginUser.getUserId()))
            .claim("username", loginUser.getUsername())
            .claim("admin", loginUser.isAdmin())
            .issuedAt(now)
            .expiration(expireAt)
            .signWith(secretKey)
            .compact();
    }

    public LoginUser parseToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        Boolean adminClaim = claims.get("admin", Boolean.class);
        return new LoginUser(
            Long.parseLong(claims.getSubject()),
            claims.get("username", String.class),
            Boolean.TRUE.equals(adminClaim)
        );
    }
}
