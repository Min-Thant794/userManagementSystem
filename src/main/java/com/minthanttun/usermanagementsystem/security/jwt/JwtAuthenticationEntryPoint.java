package com.minthanttun.usermanagementsystem.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        String authError = (String) request.getAttribute("auth_error");
        ProblemDetail problem;

        if ("ACCOUNT_SUSPENDED".equals(authError)) {
            problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN, "This account has been suspended");
            problem.setTitle("Account Suspended");
            response.setStatus(HttpStatus.FORBIDDEN.value());
        } else if ("PROFILE_INCOMPLETE".equals(authError)) {
            problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Please complete your profile before continuing");
            problem.setTitle("Profile Incomplete");
            problem.setProperty("action", "complete_profile");
            response.setStatus(HttpStatus.FORBIDDEN.value());
        } else {
            problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource");
            problem.setTitle("Unauthorized");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}