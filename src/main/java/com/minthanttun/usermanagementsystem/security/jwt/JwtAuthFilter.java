package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.security.CustomUserDetails;
import com.minthanttun.usermanagementsystem.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        System.out.println("===== JWT FILTER =====");
        System.out.println("URI: " + request.getRequestURI());
        System.out.println("METHOD: " + request.getMethod());
        System.out.println("Authorization: " + request.getHeader("Authorization"));
        System.out.println("Content-Type: " + request.getContentType());

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        System.out.println("Token present: " + !token.isBlank());
        System.out.println("Token valid: " + jwtService.isTokenValid(token));
        System.out.println("Token expired: " + jwtService.isTokenExpired(token));
        System.out.println("Token type: " + jwtService.extractTokenType(token));

        if (!jwtService.isTokenValid(token) || jwtService.isTokenExpired(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!"access".equals(jwtService.extractTokenType(token))) {
            // A refresh token (or anything else) was used where an access token belongs.
            filterChain.doFilter(request, response);
            return;
        }

        // Only set authentication if nothing has already authenticated this request.
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId = jwtService.extractUserId(token);
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;

            if (!customUserDetails.isEnabled() || !customUserDetails.isAccountNonLocked()) {
                // FR30: suspension re-checked live, even though the token itself is still valid.
                request.setAttribute("auth_error", "ACCOUNT_SUSPENDED");
                filterChain.doFilter(request, response);
                return;
            }

            String uri = request.getRequestURI();

            if (!customUserDetails.getUser().isProfileComplete()
                    && !uri.equals("/api/users/me/complete-profile")
                    && !uri.equals("/api/users/me/photo")) {

                request.setAttribute("auth_error", "PROFILE_INCOMPLETE");
                filterChain.doFilter(request, response);
                return;
            }

            System.out.println(">>> JWT AUTHENTICATION SUCCESS");
            System.out.println("User ID: " + customUserDetails.getUser().getId());
            System.out.println("Profile complete: " + customUserDetails.getUser().isProfileComplete());

            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}