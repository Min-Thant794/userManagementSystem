package com.minthanttun.usermanagementsystem.security.oauth2;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import java.time.Instant;
import java.util.*;

//Local objects only. These tests do not validate Google's JWT signature or token exchange.
final class OidcTestRequests {
    private OidcTestRequests() {}
    static OidcUserRequest request(String email, Boolean verified) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "provider-user-123");
        if (email != null) {
            claims.put("email", email);
        }

        if (verified != null) {
            claims.put("email_verified", verified);
        }

        OidcIdToken idToken = new OidcIdToken("local-test-id-token", now, now.plusSeconds(3600), claims);
        ClientRegistration client = ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope("openid", "email")
                .authorizationUri("https://example.invalid/authorize")
                .tokenUri("https://example.invalid/token")
                .jwkSetUri("https://example.invalid/jwks")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "local-test-access", now, now.plusSeconds(3600), Set.of("openid", "email"));
        return new OidcUserRequest(client, accessToken, idToken);
    }
}
