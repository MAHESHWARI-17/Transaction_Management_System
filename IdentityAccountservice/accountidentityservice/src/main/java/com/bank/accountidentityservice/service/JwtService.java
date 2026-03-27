package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    public String generateAccessToken(String customerId, List<String> roles) {
        return Jwts.builder()
                .subject(customerId)
                .claims(Map.of("roles", roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getAccessTokenExpiryMs()))
                .signWith(getKey())
                .compact();
    }

    public String generateRefreshToken(String customerId) {
        return Jwts.builder()
                .subject(customerId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshTokenExpiryMs()))
                .signWith(getKey())
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractCustomerId(String token) {
        return getClaims(token).getSubject();
    }

    public long getAccessTokenExpiryMs() {
        return jwtConfig.getAccessTokenExpiryMs();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = getClaims(token).get("roles");
        if (roles instanceof List<?>) return (List<String>) roles;
        return List.of();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtConfig.getSecret()));
    }
}
