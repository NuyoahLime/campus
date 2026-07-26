package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoginNameNormalizerTest {
    private final LoginNameNormalizer n = new LoginNameNormalizer();

    @Test void trimsWhitespace() { assertThat(n.normalize(" Alice ")).isEqualTo("Alice"); }
    @Test void preservesCaseForSame() { assertThat(n.normalize("Alice")).isEqualTo("Alice"); }
    @Test void preservesCaseForLower() { assertThat(n.normalize("alice")).isEqualTo("alice"); }
    @Test void caseSensitiveDistinction() { assertThat(n.normalize("Alice")).isNotEqualTo(n.normalize("alice")); }
    @Test void nullThrows() { assertThatThrownBy(() -> n.normalize(null)).isInstanceOf(IllegalArgumentException.class); }
    @Test void emptyThrows() { assertThatThrownBy(() -> n.normalize("")).isInstanceOf(IllegalArgumentException.class); }
    @Test void blankThrows() { assertThatThrownBy(() -> n.normalize("   ")).isInstanceOf(IllegalArgumentException.class); }
}
