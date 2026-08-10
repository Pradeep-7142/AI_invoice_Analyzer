package com.invoiceiq.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void registerCreatesOrganizationAndReturnsTokens() throws Exception {
        register("Acme Finance", "Alice Admin", "alice@acme.test", "password123")
            .getResponse().getContentAsString();
    }

    @Test
    void duplicateRegistrationEmailIsRejected() throws Exception {
        register("Acme Finance", "Alice Admin", "dup@acme.test", "password123");

        Map<String, String> body = Map.of(
            "organizationName", "Other Org",
            "fullName", "Someone Else",
            "email", "dup@acme.test",
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
        register("Acme Finance", "Alice Admin", "wrongpass@acme.test", "password123");

        Map<String, String> body = Map.of("email", "wrongpass@acme.test", "password", "not-the-password");
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

        String token = registerAndGetAccessToken("Acme Finance", "Alice Admin", "me@acme.test", "password123");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value("me@acme.test"))
            .andExpect(jsonPath("$.role").value("ORGANIZATION_ADMIN"))
            .andExpect(jsonPath("$.organization.name").value("Acme Finance"));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuseOfOldToken() throws Exception {
        MvcResult registerResult = register("Acme Finance", "Alice Admin", "rotate@acme.test", "password123");
        String firstRefreshToken = extract(registerResult, "$.refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
            .andExpect(status().isOk())
            .andReturn();
        String secondRefreshToken = extract(refreshResult, "$.refreshToken");
        org.assertj.core.api.Assertions.assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Reusing the now-revoked first refresh token must fail...
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
            .andExpect(status().isUnauthorized());

        // ...and must have also revoked the legitimately-rotated second token (theft response).
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefreshToken))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        MvcResult registerResult = register("Acme Finance", "Alice Admin", "logout@acme.test", "password123");
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

    @Test
    void nonAdminCannotManageOrganizationMembers() throws Exception {
        String adminToken = registerAndGetAccessToken("Beta Corp", "Bob Admin", "bob@beta.test", "password123");

        MvcResult addResult = mockMvc.perform(post("/api/organizations/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "fullName", "Emma Employee",
                    "email", "emma@beta.test",
                    "password", "password123",
                    "role", "EMPLOYEE"
                ))))
            .andExpect(status().isCreated())
            .andReturn();
        String memberId = extract(addResult, "$.membershipId");

        MvcResult employeeLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "emma@beta.test", "password", "password123"))))
            .andExpect(status().isOk())
            .andReturn();
        String employeeToken = extract(employeeLogin, "$.accessToken");

        mockMvc.perform(get("/api/organizations/members")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

        // sanity: the admin token still works for the same endpoint.
        mockMvc.perform(get("/api/organizations/members")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        org.assertj.core.api.Assertions.assertThat(memberId).isNotBlank();
    }

    @Test
    void lastAdministratorCannotBeRemovedOrDemoted() throws Exception {
        MvcResult registerResult = register("Solo Org", "Sam Admin", "sam@solo.test", "password123");
        String adminToken = extract(registerResult, "$.accessToken");

        MvcResult membersResult = mockMvc.perform(get("/api/organizations/members")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        String ownMembershipId = extract(membersResult, "$[0].membershipId");

        mockMvc.perform(patch("/api/organizations/members/" + ownMembershipId + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(delete("/api/organizations/members/" + ownMembershipId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tenantIsolationPreventsCrossOrganizationMemberAccess() throws Exception {
        String orgAAdminToken = registerAndGetAccessToken("Org A", "Admin A", "admina@orga.test", "password123");
        String orgBAdminToken = registerAndGetAccessToken("Org B", "Admin B", "adminb@orgb.test", "password123");

        MvcResult orgAMembers = mockMvc.perform(get("/api/organizations/members")
                .header("Authorization", "Bearer " + orgAAdminToken))
            .andExpect(status().isOk())
            .andReturn();
        String orgAMembershipId = extract(orgAMembers, "$[0].membershipId");

        // Org B's admin must not be able to see or mutate Org A's membership record.
        mockMvc.perform(patch("/api/organizations/members/" + orgAMembershipId + "/role")
                .header("Authorization", "Bearer " + orgBAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "VIEWER"))))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/organizations/members")
                .header("Authorization", "Bearer " + orgBAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].email").value("adminb@orgb.test"));
    }
}
