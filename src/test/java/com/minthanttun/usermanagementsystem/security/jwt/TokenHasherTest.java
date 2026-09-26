package com.minthanttun.usermanagementsystem.security.jwt;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

@Tag("remaining-unit")
public class TokenHasherTest {
    private final TokenHasher hasher = new TokenHasher();
    @ParameterizedTest
    @CsvSource({"abc,ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=", "''," +
            "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="})
    void matchesKnownSha256Base64Vectors(String input, String expected) {
        assertThat(hasher.hash(input)).isEqualTo(expected);
    }

    @Test
    void sameInputIsDeterministic() {
        assertThat(hasher.hash("refresh-token")).isEqualTo(hasher.hash("refresh-token"));
    }

    @Test
    void distinctExampleInputsHaveDifferentHashes() {
        assertThat(hasher.hash("refresh-token-a")).isNotEqualTo(hasher.hash("refresh-token-b"));
    }
}
