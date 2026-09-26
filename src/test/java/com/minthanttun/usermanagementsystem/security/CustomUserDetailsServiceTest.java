package com.minthanttun.usermanagementsystem.security;

import org.junit.jupiter.api.Tag;
import com.minthanttun.usermanagementsystem.user.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("remaining-unit")
public class CustomUserDetailsServiceTest {
    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService customUserDetailsService;

    @ParameterizedTest
    @ValueSource(strings = {"testuser", "id"})
    void existingUserIsWrappedWithoutLosingIdentity(String lookup) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .build();

        if (lookup.equals("username")) {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        } else {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        }

        var result = lookup.equals("username") ?
                customUserDetailsService.loadUserByUsername("testuser")
                :
                customUserDetailsService.loadUserById(user.getId());

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) result).getUser()).isSameAs(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "id"})
    void missingUserThrowsWithLookupValue(String lookup) {
        UUID id = UUID.randomUUID();
        if (lookup.equals("username")) {
            when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        } else {
            when(userRepository.findById(id)).thenReturn(Optional.empty());
        }

        assertThatThrownBy(() -> {
            if (lookup.equals("username")) {
                customUserDetailsService.loadUserByUsername("missing");
            } else {
                customUserDetailsService.loadUserById(id);
            }
        }).isInstanceOf(UsernameNotFoundException.class).hasMessage("User not found: " + (lookup.equals("username") ? "missing" : id));
    }
}
