package com.campusguinness.identity.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNormalizerTest {

    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void trimsEmail() {
        assertThat(normalizer.normalize("  User@Example.com  ")).isEqualTo("user@example.com");
    }

    @Test
    void lowercasesEmail() {
        assertThat(normalizer.normalize("USER@EXAMPLE.COM")).isEqualTo("user@example.com");
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> normalizer.normalize("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email required");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> normalizer.normalize("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid email");
    }

    @Test
    void rejectsTooLongEmail() {
        String email = "a".repeat(309) + "@example.com";
        assertThat(email).hasSize(321);
        assertThatThrownBy(() -> normalizer.normalize(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max 320");
    }

    @Test
    void doesNotStripPlusTag() {
        assertThat(normalizer.normalize("user+tag@example.com"))
                .isEqualTo("user+tag@example.com");
    }

    @Test
    void doesNotApplyProviderSpecificRules() {
        assertThat(normalizer.normalize("first.last@gmail.com"))
                .isEqualTo("first.last@gmail.com");
    }
}
