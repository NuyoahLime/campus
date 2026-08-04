package com.campusguinness.identity.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppMailPropertiesTest {

    @Test
    void trimsAndRemovesTrailingSlash() {
        var props = new AppMailProperties("no-reply@example.com", " https://example.com/base/ ");
        assertThat(props.publicFrontendUrl()).isEqualTo("https://example.com/base");
    }

    @Test
    void localhostHttpAllowedForDevelopment() {
        var props = new AppMailProperties(null, "http://localhost:5173");
        assertThat(props.publicFrontendUrl()).isEqualTo("http://localhost:5173");
    }

    @Test
    void invalidFrontendUrlsRejected() {
        assertInvalid("javascript:alert(1)");
        assertInvalid("file:///tmp/page");
        assertInvalid("/relative/path");
        assertInvalid("https://user:pass@example.com");
        assertInvalid("https://example.com/base?x=1");
        assertInvalid("https://example.com/#fragment");
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> new AppMailProperties("no-reply@example.com", value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
