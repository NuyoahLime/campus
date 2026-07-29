package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GenericUserMappingTest {

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test void noGenericUserCreationMapping() {
        var mappings = handlerMapping.getHandlerMethods();
        for (var entry : mappings.entrySet()) {
            var patterns = entry.getKey().getPatternValues();
            if (patterns != null) {
                for (String p : patterns) {
                    if (p.startsWith("/api/v1/users")) {
                        // The old UserController was deleted. Any remaining /api/v1/users mappings are unacceptable.
                        // Check that none of them are for write operations.
                        var method = entry.getKey().getMethodsCondition().getMethods();
                        boolean isWrite = method.stream().anyMatch(m -> !m.equals(org.springframework.web.bind.annotation.RequestMethod.GET));
                        if (isWrite) {
                            // Fail if any dangerous write mapping still exists at /api/v1/users
                            throw new AssertionError("Unsafe mapping exists at " + p + ": " + method);
                        }
                    }
                }
            }
        }
        // Test passes if no dangerous mapping found
    }
}
