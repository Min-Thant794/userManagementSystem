package com.minthanttun.usermanagementsystem.security.oauth2;

import com.minthanttun.usermanagementsystem.auth.OAuthAccount;
import com.minthanttun.usermanagementsystem.auth.OAuthAccountRepository;
import com.minthanttun.usermanagementsystem.user.Role;
import com.minthanttun.usermanagementsystem.user.User;
import com.minthanttun.usermanagementsystem.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Spring does the actual token exchange AND verifies the ID token's signature here.
        OidcUser oidcUser = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        String providerUserId = oidcUser.getSubject(); // OidcUser has this built in, no manual getAttribute needed
        String email = oidcUser.getEmail();
        Boolean emailVerified = oidcUser.getEmailVerified();

        if (email == null || Boolean.FALSE.equals(emailVerified)) {
            throw new OAuth2AuthenticationException("Google account has no verified email");
        }

        User user = oAuthAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(oAuthAccount -> userRepository.findById(oAuthAccount.getUser().getId())
                        .orElseThrow(() -> new OAuth2AuthenticationException("Linked user no longer exists")))
                .orElseGet(() -> linkOrCreateUser(provider, providerUserId, email));

        return new CustomOAuth2User(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private User linkOrCreateUser(String provider, String providerUserId, String email) {
        User user = userRepository.findByEmail(email)
                .map(this::verifyIfNeeded)
                .orElseGet(() -> createNewOAuthUser(email));

        OAuthAccount link = OAuthAccount.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
        oAuthAccountRepository.save(link);

        return user;
    }

    private User verifyIfNeeded(User existingUser) {
        //linking: Google already vouches for this email, so trust it even
        // if the local account never completed manual verification.
        if (!existingUser.isEmailVerified()) {
            existingUser.setEmailVerified(true);
            return userRepository.save(existingUser);
        }
        return existingUser;
    }

    private User createNewOAuthUser(String email) {
        User newUser = User.builder()
                .email(email)
                .role(Role.USER)
                .emailVerified(true)
                .build();
        return userRepository.save(newUser);
    }
}