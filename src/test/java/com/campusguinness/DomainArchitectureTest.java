package com.campusguinness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that domain model packages do not depend on infrastructure frameworks.
 * Domain code under *.internal.domain must be free of Spring, JPA, Hibernate,
 * Servlet, Jackson, MinIO, and Testcontainers imports.
 */
class DomainArchitectureTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "org.springframework",
            "jakarta.persistence",
            "org.hibernate",
            "jakarta.servlet",
            "com.fasterxml.jackson",
            "io.minio",
            "org.testcontainers"
    );

    @Test
    @DisplayName("Domain packages must not import Spring, JPA, Hibernate, or other infrastructure")
    void domainPackagesFreeOfInfrastructureDependencies() throws IOException {
        Path domainRoot = Paths.get("src/main/java/com/campusguinness");
        assertThat(Files.exists(domainRoot)).isTrue();

        try (Stream<Path> files = Files.walk(domainRoot)) {
            List<String> violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String pathStr = p.toString().replace('\\', '/');
                        return pathStr.contains("/internal/domain/");
                    })
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .filter(line -> {
                                        String trimmed = line.trim();
                                        if (!trimmed.startsWith("import ")) return false;
                                        return FORBIDDEN_IMPORTS.stream()
                                                .anyMatch(trimmed::contains);
                                    })
                                    .map(line -> p.getFileName() + ": " + line.trim());
                        } catch (IOException e) {
                            return Stream.of(p.getFileName() + ": ERROR reading file");
                        }
                    })
                    .toList();

            assertThat(violations)
                    .as("Domain classes must not depend on infrastructure frameworks")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Domain classes exist under *.internal.domain packages")
    void domainClassesExist() throws IOException {
        Path domainRoot = Paths.get("src/main/java/com/campusguinness");
        try (Stream<Path> files = Files.walk(domainRoot)) {
            long domainFileCount = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String pathStr = p.toString().replace('\\', '/');
                        return pathStr.contains("/internal/domain/");
                    })
                    .count();

            assertThat(domainFileCount)
                    .as("Domain classes count must increase with each batch")
                    .isGreaterThanOrEqualTo(120);
        }
    }
}
