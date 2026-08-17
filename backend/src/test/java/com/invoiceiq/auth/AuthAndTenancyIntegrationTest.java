package com.invoiceiq.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthAndTenancyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerFirstUserBecomesAdminAndReturnsTokens() throws Exception {
        MvcResult result = register("Alice Admin", "alice@invoiceiq.test", "password123");
        String accessToken = extract(result, "$.accessToken");
        org.assertj.core.api.Assertions.assertThat(accessToken).isNotBlank();
    }

    @Test
    void duplicateRegistrationEmailIsRejected() throws Exception {
        register("Bob First", "dup@invoiceiq.test", "password123");

        Map<String, String> body = Map.of(
            "fullName", "Someone Else",
            "email", "dup@invoiceiq.test",
            "password", "password123"
        );
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        register("Charlie", "wrongpass@invoiceiq.test", "password123");

        Map<String, String> body = Map.of("email", "wrongpass@invoiceiq.test", "password", "not-the-password");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meRequiresAuthenticationAndReturnsCurrentUserWhenPresent() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        String token = registerAndGetAccessToken("Diana", "me@invoiceiq.test", "password123");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("me@invoiceiq.test"));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuseOfOldToken() throws Exception {
        MvcResult registerResult = register("Eva", "rotate@invoiceiq.test", "password123");
        String firstRefreshToken = extract(registerResult, "$.refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
            .andExpect(status().isOk())
            .andReturn();
        String secondRefreshToken = extract(refreshResult, "$.refreshToken");
        org.assertj.core.api.Assertions.assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Reusing the now-revoked first refresh token must fail
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
            .andExpect(status().isUnauthorized());

        // And theft response revokes second token
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefreshToken))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        MvcResult registerResult = register("Frank", "logout@invoiceiq.test", "password123");
        String refreshToken = extract(registerResult, "$.refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isUnauthorized());
    }
}
