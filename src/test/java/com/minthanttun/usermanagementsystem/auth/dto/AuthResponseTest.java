package com.minthanttun.usermanagementsystem.auth.dto;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class AuthResponseTest {
    @ParameterizedTest
    @CsvSource({"900000,900", "1999,1", "999,0", "0,0"})
    void mapsAccessTokenBearerSchemeAndWholeSeconds(long milliseconds, long seconds) {
        AuthResponse result = AuthResponse.of("access-token", milliseconds);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(seconds);
    }
}
