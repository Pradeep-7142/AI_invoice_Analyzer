package com.invoiceiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.dto.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Runs before Spring MVC's DispatcherServlet, so GlobalExceptionHandler
 * never sees these — we write the same ApiError shape by hand to keep the
 * response contract consistent for the frontend.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(401, "UNAUTHORIZED", "Authentication is required to access this resource.", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
