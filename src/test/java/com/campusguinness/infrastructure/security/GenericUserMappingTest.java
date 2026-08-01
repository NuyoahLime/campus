package com.campusguinness.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GenericUserMappingTest {

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test void userWriteEndpointsAreSuperAdminOnly() {
        var mappings = handlerMapping.getHandlerMethods();
        for (var entry : mappings.entrySet()) {
            var patterns = entry.getKey().getPatternValues();
            if (patterns != null) {
                for (String p : patterns) {
                    if (p.startsWith("/api/v1/users")) {
                        var method = entry.getKey().getMethodsCondition().getMethods();
                        boolean isWrite = method.stream().anyMatch(m -> !m.equals(RequestMethod.GET));
                        if (isWrite) {
                            // Write endpoints at /api/v1/users MUST have @PreAuthorize("hasRole('SUPER_ADMIN')")
                            var handler = entry.getValue();
                            var beanType = handler.getBeanType();
                            var preAuth = beanType.getAnnotation(PreAuthorize.class);
                            if (preAuth == null) {
                                // Check method-level
                                preAuth = handler.getMethod().getAnnotation(PreAuthorize.class);
                            }
                            assertThat(preAuth)
                                    .as("Write endpoint " + p + " must have @PreAuthorize")
                                    .isNotNull();
                            assertThat(preAuth.value())
                                    .as("Write endpoint " + p + " must require SUPER_ADMIN")
                                    .contains("SUPER_ADMIN");
                        }
                    }
                }
            }
        }
    }
}
