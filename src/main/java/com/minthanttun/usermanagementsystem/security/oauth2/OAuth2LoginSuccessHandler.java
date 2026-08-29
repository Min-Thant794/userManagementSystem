package com.minthanttun.usermanagementsystem.security.oauth2;

import com.minthanttun.usermanagementsystem.security.jwt.CookieUtil;
import com.minthanttun.usermanagementsystem.security.jwt.TokenIssuer;
import com.minthanttun.usermanagementsystem.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenIssuer tokenIssuer;
    private final CookieUtil cookieUtil;

    @Value("${app.oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        var tokens = tokenIssuer.issueTokenPair(user);
        cookieUtil.setRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshTokenExpiryMs());

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("profileComplete", user.isProfileComplete())
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}