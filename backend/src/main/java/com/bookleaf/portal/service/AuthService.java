package com.bookleaf.portal.service;

import com.bookleaf.portal.config.AppProperties;
import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Role;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.dto.AuthDtos.AuthResponse;
import com.bookleaf.portal.dto.AuthDtos.LoginRequest;
import com.bookleaf.portal.dto.AuthDtos.UserSummary;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.UserRepository;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, token refresh and logout.
 *
 * <p><b>Token model.</b> A short-lived access token (30 min) is returned in the
 * response body and sent by the client in the {@code Authorization} header. A
 * long-lived refresh token (7 days) is delivered in an httpOnly, SameSite=Strict
 * cookie and can only be redeemed by {@code POST /api/v1/auth/refresh}. Keeping
 * the refresh token out of JavaScript means an XSS bug cannot exfiltrate it.</p>
 */
@Service
public class AuthService {

    public static final String REFRESH_COOKIE = "bookleaf_refresh";

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final AuthorRepository authorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository,
                       AuthorRepository authorRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties properties) {
        this.userRepository = userRepository;
        this.authorRepository = authorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same message and roughly the same work for unknown email vs wrong password.
            log.info("Failed login for {}", request.email());
            throw ApiException.unauthorized("Email or password is incorrect");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated. Please contact BookLeaf support.");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        Author author = user.getRole() == Role.AUTHOR
                ? authorRepository.findByUserId(user.getId()).orElse(null)
                : null;

        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRole().name(),
                author == null ? null : author.getId());
        String refreshToken = jwtService.createRefreshToken(user.getId(), user.getEmail(), user.getRole().name(),
                author == null ? null : author.getId());

        writeRefreshCookie(response, refreshToken);
        return new AuthResponse(accessToken, "Bearer", jwtService.accessTokenSeconds(), refreshToken,
                toSummary(user, author));
    }

    /**
     * Redeems a refresh token for a new access token.
     *
     * <p>The token may arrive in the request body (a tab keeping its own copy) or in the
     * httpOnly cookie (the shared session). The body wins when both are present, so a tab
     * signed in as one account is never switched to another account's session by a
     * different tab's refresh.</p>
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        Claims claims = jwtService.parse(refreshToken, JwtService.TYPE_REFRESH);
        if (claims == null) {
            throw ApiException.unauthorized("Your session has expired. Please sign in again.");
        }
        User user = userRepository.findById(java.util.UUID.fromString(claims.getSubject()))
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Your session is no longer valid. Please sign in again."));

        Author author = user.getRole() == Role.AUTHOR
                ? authorRepository.findByUserId(user.getId()).orElse(null)
                : null;

        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRole().name(),
                author == null ? null : author.getId());
        String rotatedRefreshToken = jwtService.createRefreshToken(user.getId(), user.getEmail(),
                user.getRole().name(), author == null ? null : author.getId());

        writeRefreshCookie(response, rotatedRefreshToken);
        return new AuthResponse(accessToken, "Bearer", jwtService.accessTokenSeconds(), rotatedRefreshToken,
                toSummary(user, author));
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(isSecureCookie())
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(isSecureCookie())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(properties.getJwt().getRefreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Secure cookies require HTTPS. This is configuration-driven rather than
     * guessed from the environment, so local HTTP development stays simple while
     * deployed environments opt in explicitly.
     */
    private boolean isSecureCookie() {
        return properties.getSecurity().isSecureCookies();
    }

    public UserSummary toSummary(User user, Author author) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                author == null ? null : author.getId(),
                author == null ? null : author.getAuthorCode());
    }

    public UserSummary toSummary(AppUserPrincipal principal) {
        return new UserSummary(
                principal.getUserId(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRole(),
                principal.getAuthorId(),
                principal.getAuthorId() == null ? null
                        : authorRepository.findById(principal.getAuthorId())
                        .map(Author::getAuthorCode)
                        .orElse(null));
    }
}
