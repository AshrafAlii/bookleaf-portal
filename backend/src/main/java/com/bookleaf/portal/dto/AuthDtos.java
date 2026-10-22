package com.bookleaf.portal.dto;

import com.bookleaf.portal.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Authentication payloads.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be a valid address")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    /**
     * @param expiresInSeconds lifetime of the access token; the client refreshes before it elapses
     * @param refreshToken      the long-lived token. It is also set as an httpOnly cookie, but it is
     *                          returned here as well so a browser tab can keep its own copy — that is
     *                          what lets an author session and an admin session be open side by side.
     */
    public record AuthResponse(String accessToken,
                                String tokenType,
                                long expiresInSeconds,
                                String refreshToken,
                                UserSummary user) {
    }

    public record UserSummary(UUID id,
                               String email,
                               String fullName,
                               Role role,
                               UUID authorId,
                               String authorCode) {
    }
}
