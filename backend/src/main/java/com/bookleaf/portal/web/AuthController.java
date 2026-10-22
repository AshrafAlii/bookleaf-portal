package com.bookleaf.portal.web;

import com.bookleaf.portal.dto.AuthDtos.AuthResponse;
import com.bookleaf.portal.dto.AuthDtos.LoginRequest;
import com.bookleaf.portal.dto.AuthDtos.RefreshRequest;
import com.bookleaf.portal.dto.AuthDtos.UserSummary;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password",
            description = "Returns a short-lived access token and sets an httpOnly refresh cookie.")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Email or password is incorrect")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return authService.login(request, response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    @ApiResponse(responseCode = "200", description = "New access token issued")
    @ApiResponse(responseCode = "401", description = "Refresh token is missing or expired")
    public AuthResponse refresh(@CookieValue(name = AuthService.REFRESH_COOKIE, required = false) String cookieToken,
                                @RequestBody(required = false) RefreshRequest body,
                                HttpServletResponse response) {
        // The body wins: a tab that keeps its own refresh token must not be
        // handed another tab's session by the shared cookie.
        String token = body != null && body.refreshToken() != null && !body.refreshToken().isBlank()
                ? body.refreshToken()
                : cookieToken;
        if (token == null || token.isBlank()) {
            throw com.bookleaf.portal.exception.ApiException.unauthorized(
                    "No refresh token was supplied. Please sign in again.");
        }
        return authService.refresh(token, response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the refresh cookie")
    public void logout(HttpServletResponse response) {
        authService.logout(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the profile of the currently authenticated user")
    public UserSummary me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.toSummary(principal);
    }
}
