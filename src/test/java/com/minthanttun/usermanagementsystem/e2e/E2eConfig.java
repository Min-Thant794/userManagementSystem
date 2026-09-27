package com.minthanttun.usermanagementsystem.e2e;

import com.minthanttun.usermanagementsystem.admin.*;
import com.minthanttun.usermanagementsystem.audit.AuditService;
import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.common.exception.GlobalExceptionHandler;
import com.minthanttun.usermanagementsystem.config.*;
import com.minthanttun.usermanagementsystem.security.CustomUserDetailsService;
import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.security.oauth2.*;
import com.minthanttun.usermanagementsystem.security.ratelimit.RateLimitFilter;
import com.minthanttun.usermanagementsystem.security.session.UserAgentParsingService;
import com.minthanttun.usermanagementsystem.user.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

// A web context with production controllers and production SecurityConfig.
// No component scan: this avoids accidentally loading the older IntegrationTestConfig.
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
@EnableAutoConfiguration
@EntityScan("com.minthanttun.usermanagementsystem")
@EnableJpaRepositories("com.minthanttun.usermanagementsystem")
@EnableCaching
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@Import({SecurityConfig.class, RedisCacheConfig.class, RateLimitConfig.class,
        JwtAuthFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        RateLimitFilter.class, CustomUserDetailsService.class, JwtService.class, TokenHasher.class,
        TokenIssuer.class, SessionRevocationService.class, CookieUtil.class,
        CustomOidcUserService.class, OAuth2LoginSuccessHandler.class,
        AuthController.class, UserController.class, AdminUserController.class, AdminAuditLogController.class,
        GlobalExceptionHandler.class, AuthService.class, LoginAttemptService.class,
        PasswordResetService.class, EmailVerificationService.class, EmailService.class,
        SessionService.class, UserAgentParsingService.class, UserService.class,
        AdminUserService.class, AuditService.class})
public class E2eConfig {
    @Bean ClientRegistrationRepository clientRegistrationRepository() {
        // Satisfies oauth2Login() wiring without OIDC discovery or real credentials.
        var registration = ClientRegistration.withRegistrationId("test")
                .clientId("http-test-client").clientSecret("not-a-real-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email")
                .authorizationUri("https://identity.example.invalid/authorize")
                .tokenUri("https://identity.example.invalid/token")
                .jwkSetUri("https://identity.example.invalid/jwks")
                .userInfoUri("https://identity.example.invalid/userinfo")
                .userNameAttributeName("sub").clientName("Test provider").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}