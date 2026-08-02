package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LoginNameNormalizerTest {

    private final LoginNameNormalizer n = new LoginNameNormalizer();

    @Test void trimsWhitespace() { assertThat(n.normalize(" Alice ")).isEqualTo("alice"); }

    @Test void lowercasesUppercase() { assertThat(n.normalize("ALICE")).isEqualTo("alice"); }

    @Test void lowercasesMixed() { assertThat(n.normalize("AlIcE")).isEqualTo("alice"); }

    @Test void caseInsensitiveEquality() { assertThat(n.normalize("Alice")).isEqualTo(n.normalize("alice")); }

    @Test void fullwidthNormalized() {
        // Fullwidth 'Ａ' (U+FF21) → ASCII 'a' via NFKC
        assertThat(n.normalize("Ａlice")).isEqualTo("alice");
    }

    @Test void doubleNormalizationIsIdempotent() {
        String once = n.normalize("  ＡＬＩＣＥ  ");
        String twice = n.normalize(once);
        assertThat(twice).isEqualTo(once).isEqualTo("alice");
    }

    @Test void turkishIHandledByLocaleRoot() {
        assertThat(n.normalize("INIT")).isEqualTo("init");
    }

    @Test void nullThrows() { assertThatThrownBy(() -> n.normalize(null)).isInstanceOf(IllegalArgumentException.class); }

    @Test void emptyThrows() { assertThatThrownBy(() -> n.normalize("")).isInstanceOf(IllegalArgumentException.class); }

    @Test void blankThrows() { assertThatThrownBy(() -> n.normalize("   ")).isInstanceOf(IllegalArgumentException.class); }
}
