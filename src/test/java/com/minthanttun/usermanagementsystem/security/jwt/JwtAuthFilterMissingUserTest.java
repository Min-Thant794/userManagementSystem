package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.security.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterMissingUserTest {

    @Mock
    JwtService jwtService;

    @Mock
    CustomUserDetailsService userDetailsService;

    @Mock
    FilterChain chain;

    @InjectMocks
    JwtAuthFilter filter;

    private final UUID userId = UUID.randomUUID();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();

        request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer token");

        response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.extractTokenType("token")).thenReturn("access");
        when(jwtService.extractUserId("token")).thenReturn(userId);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletedUserContinuesUnauthenticatedExactlyOnce() throws Exception {
        when(userDetailsService.loadUserById(userId))
                .thenThrow(new UsernameNotFoundException("User not found"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        assertThat(request.getAttribute("auth_error")).isNull();

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void databaseOutageIsNotMistakenForADeletedUser() {
        var failure = new DataAccessResourceFailureException(
                "Database unavailable"
        );

        when(userDetailsService.loadUserById(userId)).thenThrow(failure);

        assertThatThrownBy(() ->
                filter.doFilter(request, response, chain)
        ).isSameAs(failure);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();

        verifyNoInteractions(chain);
    }

    @Test
    void exceptionFromTheRemainingChainIsNotSwallowedOrRetried()
            throws Exception {

        when(userDetailsService.loadUserById(userId))
                .thenThrow(new UsernameNotFoundException("Deleted user"));

        var downstreamFailure =
                new UsernameNotFoundException("Downstream failure");

        doThrow(downstreamFailure)
                .when(chain)
                .doFilter(request, response);

        assertThatThrownBy(() ->
                filter.doFilter(request, response, chain)
        ).isSameAs(downstreamFailure);

        verify(chain, times(1)).doFilter(request, response);
    }
}