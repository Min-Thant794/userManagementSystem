package com.minthanttun.usermanagementsystem.security.oauth2;

import com.minthanttun.usermanagementsystem.auth.*;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomOidcUserServiceTest {
    @Mock UserRepository userRepository;
    @Mock OAuthAccountRepository oAuthAccountRepository;
    private CustomOidcUserService customOidcUserService;

    @BeforeEach
    void setUp() {
        customOidcUserService = new CustomOidcUserService(userRepository, oAuthAccountRepository);
        customOidcUserService.setRetrieveUserInfo(request -> false);
    }

    private User existingUser(boolean verified) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .emailVerified(verified)
                .role(Role.USER)
                .build();
    }

    @Test
    void existingLinkReloadsUserWithoutCreatingAnotherLink() {
        User user = existingUser(true);
        OAuthAccount link = OAuthAccount.builder().user(user).provider("GOOGLE").providerUserId("provider-user-123").build();
        when(oAuthAccountRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-123")).thenReturn(Optional.of(link));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        CustomOAuth2User result = (CustomOAuth2User) customOidcUserService.loadUser(OidcTestRequests.request(user.getEmail(), true));
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getIdToken().getSubject()).isEqualTo("provider-user-123");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any());
        verify(oAuthAccountRepository, never()).save(any());
    }

    @Test
    void missingLinkedUserFails() {
        User user = existingUser(true);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-123"))
                .thenReturn(Optional.of(OAuthAccount.builder().user(user).build()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customOidcUserService.loadUser(OidcTestRequests.request(user.getEmail(), true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(userRepository, never()).save(any()); verify(oAuthAccountRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linksExistingEmailAndVerifiesOnlyWhenNeeded(boolean alreadyVerified) {
        User user = existingUser(alreadyVerified);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-123")).thenReturn(Optional.empty());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        if (!alreadyVerified) {
            when(userRepository.save(user)).thenReturn(user);
        }
        CustomOAuth2User result = (CustomOAuth2User) customOidcUserService.loadUser(OidcTestRequests.request(user.getEmail(), true));
        assertThat(result.getUser()).isSameAs(user);
        assertThat(user.isEmailVerified()).isTrue();
        if (alreadyVerified) {
            verify(userRepository, never()).save(any());
        } else {
            verify(userRepository).save(user);
        }
        assertSavedLink(user);
    }

    @Test
    void newEmailCreatesVerifiedUserAndNormalizedProviderLink() {
        when(oAuthAccountRepository.findByProviderAndProviderUserId("GOOGLE", "provider-user-123")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        CustomOAuth2User result = (CustomOAuth2User) customOidcUserService.loadUser(OidcTestRequests.request("new@example.com", true));
        User user = result.getUser();
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.isProfileComplete()).isFalse();
        assertSavedLink(user);
    }

    private void assertSavedLink(User user) {
        ArgumentCaptor<OAuthAccount> link = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(oAuthAccountRepository).save(link.capture());
        assertThat(link.getValue().getUser()).isSameAs(user);
        assertThat(link.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(link.getValue().getProviderUserId()).isEqualTo("provider-user-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-email", "unverified"})
    void invalidEmailIdentityIsRejectedBeforeDatabaseAccess(String scenario) {
        String email = scenario.equals("missing-email") ? null : "user@example.com";
        Boolean verified = !scenario.equals("unverified");
        assertThatThrownBy(() -> customOidcUserService.loadUser(OidcTestRequests.request(email, verified)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verifyNoInteractions(userRepository, oAuthAccountRepository);
    }
}
