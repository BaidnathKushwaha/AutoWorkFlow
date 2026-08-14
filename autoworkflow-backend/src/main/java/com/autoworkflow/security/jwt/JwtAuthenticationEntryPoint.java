package com.autoworkflow.security.jwt;

import com.autoworkflow.util.JsonUtils;
import com.autoworkflow.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        ErrorResponse error = new ErrorResponse(
                "Authentication required. Provide a valid Bearer token.",
                request.getRequestURI(),
                HttpStatus.UNAUTHORIZED.value(),
                null);
        response.getWriter().write(JsonUtils.toJson(error));
    }
}
