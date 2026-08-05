package com.campusguinness.identity.internal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationCodeGeneratorTest {

    @Test
    void generatedCodesAreUrlSafeHighEntropyAndDifferent() {
        var generator = new SecureRandomInvitationCodeGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotBlank();
        assertThat(first).hasSizeGreaterThanOrEqualTo(43);
        assertThat(first).doesNotContain("+", "/", "=");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void hasherStoresNonRawValueAndMatchesOnlyCorrectCode() {
        var passwordHasher = new BCryptPasswordHasher(new BCryptPasswordEncoder(4));
        var hasher = new PasswordHasherInvitationCodeHasher(passwordHasher);

        String raw = "invite-code-value";
        String hash = hasher.hash(raw);

        assertThat(hash).isNotEqualTo(raw);
        assertThat(hasher.matches(raw, hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }
}
