package com.invoiceiq;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoiceiq.config.SecurityConfig;
import com.invoiceiq.controller.HealthController;
import com.invoiceiq.security.JwtAuthenticationFilter;
import com.invoiceiq.security.JwtService;
import com.invoiceiq.security.RestAccessDeniedHandler;
import com.invoiceiq.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
    RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")));
    }
}
