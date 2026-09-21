package com.minthanttun.usermanagementsystem.security.oauth2;

import com.minthanttun.usermanagementsystem.security.jwt.*;
import com.minthanttun.usermanagementsystem.user.User;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OAuth2LoginSuccessHandlerTest {
    @Mock TokenIssuer tokenIssuer;
    @Mock CookieUtil cookieUtil;
    @InjectMocks OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsSessionSetsCookieAndRedirectsWithProfileState(boolean complete) throws Exception {
        ReflectionTestUtils.setField(oAuth2LoginSuccessHandler, "frontendRedirectUri", "http://localhost:3000/callback");
        User user = User.builder().id(UUID.randomUUID()).username(complete ? "testuser" : null).phoneNumber(complete ? "+6591111111" : null).build();
        CustomOAuth2User principal = new CustomOAuth2User(user, OidcTestRequests.request("test@example.com", true).getIdToken(), null);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(tokenIssuer.issueNewSession(user, request)).thenReturn(new TokenIssuer.IssuedTokens("access-secret", "refresh-secret", 604_800_000L));

        oAuth2LoginSuccessHandler.onAuthenticationSuccess(request, response, auth);
        verify(tokenIssuer).issueNewSession(user, request);
        verify(cookieUtil).setRefreshTokenCookie(response, "refresh-secret", 604_800_000L);
        assertThat(response.getStatus()).isEqualTo(302);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/callback?profileComplete=" + complete).doesNotContain("access-secret", "refresh-secret");
        assertThat(UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build().getQueryParams()).containsOnlyKeys("profileComplete");
    }
}
