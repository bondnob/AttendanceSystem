package com.attendance.auth.security;

import com.attendance.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expireHours;

    public JwtTokenProvider(@Value("${attendance.jwt.secret}") String secret,
                            @Value("${attendance.jwt.expire-hours:12}") long expireHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret 至少需要 32 个字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, String username, String roleCode, Long orgUnitId) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(expireHours * 3600);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roleCode", roleCode)
                .claim("orgUnitId", orgUnitId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public CurrentUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            return CurrentUser.builder()
                    .userId(Long.valueOf(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .roleCode(claims.get("roleCode", String.class))
                    .orgUnitId(Long.valueOf(String.valueOf(claims.get("orgUnitId"))))
                    .build();
        } catch (Exception ex) {
            throw new BizException("token 无效或已过期");
        }
    }

    public long getExpireSeconds() {
        return expireHours * 3600;
    }
}
