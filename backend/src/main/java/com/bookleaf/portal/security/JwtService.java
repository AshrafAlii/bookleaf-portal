package com.bookleaf.portal.security;

import com.bookleaf.portal.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the two JWTs used by the portal.
 *
 * <p>Access tokens are short-lived and sent in the {@code Authorization} header.
 * Refresh tokens are long-lived and handed out in an httpOnly cookie so a stolen
 * access token cannot be renewed. The token type claim stops a refresh token from
 * being replayed as an access token (and vice versa).</p>
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UUID userId, String email, String role, UUID authorId) {
        return createToken(userId, email, role, authorId, TYPE_ACCESS,
                properties.getJwt().getAccessTokenTtl());
    }

    public String createRefreshToken(UUID userId, String email, String role, UUID authorId) {
        return createToken(userId, email, role, authorId, TYPE_REFRESH,
                properties.getJwt().getRefreshTokenTtl());
    }

    private String createToken(UUID userId, String email, String role, UUID authorId, String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim("typ", type)
                .claim("email", email)
                .claim("role", role);
        if (authorId != null) {
            builder.claim("authorId", authorId.toString());
        }
        return builder.signWith(key).compact();
    }

    /**
     * Parses and verifies a token. Returns {@code null} for anything that is not
     * a currently valid, correctly typed token — callers treat that as "anonymous".
     */
    public Claims parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get("typ", String.class))) {
                return null;
            }
            return claims;
        } catch (Exception ex) {
            return null;
        }
    }

    public long accessTokenSeconds() {
        return properties.getJwt().getAccessTokenTtl().toSeconds();
    }
}
