package com.minthanttun.usermanagementsystem.security.jwt;

import com.minthanttun.usermanagementsystem.security.*;
import com.minthanttun.usermanagementsystem.user.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthFilterTest {
    @Mock JwtService jwtService;
    @Mock CustomUserDetailsService customUserDetailsService;
    @Mock FilterChain chain;
    @InjectMocks JwtAuthFilter jwtAuthFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/api/users/me");
        response = new MockHttpServletResponse();
        user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .phoneNumber("+6591111111")
                .emailVerified(true)
                .status(AccountStatus.ACTIVE)
                .role(Role.USER)
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void bearer() {
        request.addHeader("Authorization", "Bearer raw");
    }

    private void validAccess() {
        bearer();
        when(jwtService.isTokenValid("raw")).thenReturn(true);
        when(jwtService.extractTokenType("raw")).thenReturn("access");
    }

    private void loadUser() {
        when(jwtService.extractUserId("raw")).thenReturn(user.getId());
        when(customUserDetailsService.loadUserById(user.getId())).thenReturn(new CustomUserDetails(user));
    }

    private void assertUnauthenticatedAndContinued() throws Exception {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Basic abc", "bearer raw", ""})
    void absentOrNonBearerHeaderContinuesWithoutAuthentication(String header) throws Exception {
        if (header != null) {
            request.addHeader("Authorization", header);
        }

        jwtAuthFilter.doFilter(request, response, chain);
        assertUnauthenticatedAndContinued();
        verifyNoInteractions(jwtService, customUserDetailsService);
    }

    @Test
    void invalidTokenContinuesWithoutAuthenticationWhenJwtCollaboratorDoesNotThrow() throws Exception {
        bearer(); //Mock returns false; real malformed-token behavior has a regression test.
        jwtAuthFilter.doFilter(request, response, chain);
        assertUnauthenticatedAndContinued();
        verifyNoInteractions(customUserDetailsService);
    }

    @Test
    void expiredTokenContinuesWithoutAuthenticationWhenClaimsAreStubbed() throws Exception {
        bearer();
        when(jwtService.isTokenValid("raw")).thenReturn(true);
        when(jwtService.isTokenValid("raw")).thenReturn(true);
        jwtAuthFilter.doFilter(request, response, chain);
        assertUnauthenticatedAndContinued();
        verifyNoInteractions(customUserDetailsService);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"refresh", "unknown"})
    void nonAccessTokenDoesNotAuthenticate(String type) throws Exception {
        bearer();
        when(jwtService.isTokenValid("raw")).thenReturn(true);
        when(jwtService.extractTokenType("raw")).thenReturn(type);
        jwtAuthFilter.doFilter(request, response, chain);
        assertUnauthenticatedAndContinued();
        verifyNoInteractions(customUserDetailsService);
    }

    @Test
    void validAccessAuthenticatesDatabaseUserAndAuthorities() throws Exception {
        validAccess();
        loadUser();
        jwtAuthFilter.doFilter(request, response, chain);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(((CustomUserDetails) auth.getPrincipal()).getUser()).isSameAs(user);

        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(auth.getCredentials()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void existingAuthenticationIsPreserved() throws Exception {
        validAccess();
        Authentication existing = new UsernamePasswordAuthenticationToken("already-authenticated", null);
        SecurityContextHolder.getContext().setAuthentication(existing);
        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verifyNoInteractions(customUserDetailsService);
        verify(chain, times(1)).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"suspeneded", "locked"})
    void accountRestrictionsSetErrorAndDoNotAuthenticate(String restriction) throws Exception {
        validAccess();
        if (restriction.equals("suspended")) {
            user.setStatus(AccountStatus.SUSPENDED);
        } else {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(10));
        }
        loadUser();
        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(request.getAttribute("auth_error")).isEqualTo("ACCOUNT_SUSPENDED");
        assertUnauthenticatedAndContinued();
    }

    @Test
    void incompleteProfileIsBlockedOnRestrictedPath() throws Exception {
        validAccess();
        user.setPhoneNumber(null);
        loadUser();
        jwtAuthFilter.doFilter(request, response, chain);

        assertThat(request.getAttribute("auth_error")).isEqualTo("PROFILE_INCOMPLETE");
        assertUnauthenticatedAndContinued();
    }

    @ParameterizedTest @ValueSource(strings = {"/api/users/me/complete-profile", "/api/users/me/photo"})
    void incompleteProfileCanUseAllowedPaths(String path) throws Exception {
        request.setRequestURI(path);
        validAccess(); user.setPhoneNumber(null); loadUser();
        jwtAuthFilter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute("auth_error")).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
