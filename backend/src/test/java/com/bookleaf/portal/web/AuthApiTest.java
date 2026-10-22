package com.bookleaf.portal.web;

import com.bookleaf.portal.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTestBase {

    @Test
    @DisplayName("login returns an access token and the user profile")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", AUTHOR_EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                // The refresh token is returned as well as set as a cookie, so a
                // browser tab can keep its own copy and run two accounts at once.
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("AUTHOR"))
                .andExpect(jsonPath("$.user.authorCode").value("AUTH001"));
    }

    @Test
    @DisplayName("a refresh token in the body takes precedence over the shared cookie")
    void bodyTokenWinsOverCookie() throws Exception {
        String authorRefresh = json(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", AUTHOR_EMAIL, "password", PASSWORD))))
                .andReturn()).path("refreshToken").asText();

        // Sign in as the admin in the same session, so the cookie now holds the
        // admin's refresh token.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", ADMIN_EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk());

        // Refreshing with the author's own token must still return the author.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("refreshToken", authorRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(AUTHOR_EMAIL))
                .andExpect(jsonPath("$.user.role").value("AUTHOR"));
    }

    @Test
    @DisplayName("login rejects a wrong password with a generic message")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", AUTHOR_EMAIL, "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    @DisplayName("login rejects an unknown email with the same message")
    void loginWithUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", "nobody@email.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    @Test
    @DisplayName("validation failures are reported per field")
    void loginValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", "not-an-email", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("protected endpoints require a token")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/author/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/auth/me returns the signed-in profile")
    void meReturnsProfile() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/auth/me"), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(AUTHOR_EMAIL))
                .andExpect(jsonPath("$.role").value("AUTHOR"));
    }
}
