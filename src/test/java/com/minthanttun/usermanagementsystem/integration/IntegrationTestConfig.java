package com.minthanttun.usermanagementsystem.integration;

import com.minthanttun.usermanagementsystem.admin.AdminUserService;
import com.minthanttun.usermanagementsystem.audit.AuditService;
import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.common.TokenCleanupJob;
import com.minthanttun.usermanagementsystem.config.RedisCacheConfig;
import com.minthanttun.usermanagementsystem.security.CustomUserDetailsService;
import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.user.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

//Explicit imports avoid starting OAuth, Cloudinary, HTTP filters or scheduled jobs.
//This tests the real persistance/services/cahce stack, not the HTTP application context.

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan("com.minthanttun.usermanagementsystem")
@EnableJpaRepositories("com.minthanttun.usermanagementsystem")
@EnableCaching
@Import({RedisCacheConfig.class, AuthService.class, UserService.class,
        AdminUserService.class, AuditService.class, LoginAttemptService.class,
        PasswordResetService.class, EmailVerificationService.class, EmailService.class,
        JwtService.class, TokenHasher.class, TokenIssuer.class,
        SessionRevocationService.class, CustomUserDetailsService.class, TokenCleanupJob.class})
public class IntegrationTestConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    @Bean AuthenticationManager authenticationManager(CustomUserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
