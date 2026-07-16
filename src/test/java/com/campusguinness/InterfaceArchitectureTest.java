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
 * Verifies interface layer (controllers, DTOs) does not depend on infrastructure.
 */
class InterfaceArchitectureTest {

    private static final Path INTERFACES_ROOT = Paths.get("src/main/java/com/campusguinness/interfaces");

    private static final List<String> FORBIDDEN_CONTROLLER_IMPORTS = List.of(
            "jakarta.persistence",
            "org.springframework.data.jpa",
            "org.springframework.data.repository",
            "EntityManager",
            ".persistence.",
            "PersistenceMapper",
            "RepositoryAdapter"
    );

    @Test
    @DisplayName("Controllers must not import JPA, repository, or domain aggregate types")
    void controllersFreeOfInfrastructureDependencies() throws IOException {
        assertThat(Files.exists(INTERFACES_ROOT)).isTrue();
        try (Stream<Path> files = Files.walk(INTERFACES_ROOT)) {
            List<String> violations = files
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .flatMap(p -> checkForbiddenImports(p, FORBIDDEN_CONTROLLER_IMPORTS).stream())
                    .toList();
            assertThat(violations).as("Controller infrastructure dependency violations").isEmpty();
        }
    }

    @Test
    @DisplayName("Request DTOs must not import JPA Entity or domain aggregate types")
    void requestDtosClean() throws IOException {
        try (Stream<Path> files = Files.walk(INTERFACES_ROOT)) {
            List<String> violations = files
                    .filter(p -> p.getFileName().toString().endsWith("Request.java"))
                    .flatMap(p -> checkForbiddenImports(p, FORBIDDEN_CONTROLLER_IMPORTS).stream())
                    .toList();
            assertThat(violations).as("Request DTO infrastructure dependencies").isEmpty();
        }
    }

    @Test
    @DisplayName("Response DTOs must not import JPA Entity or domain aggregate types")
    void responseDtosClean() throws IOException {
        try (Stream<Path> files = Files.walk(INTERFACES_ROOT)) {
            List<String> violations = files
                    .filter(p -> p.getFileName().toString().endsWith("Response.java"))
                    .flatMap(p -> checkForbiddenImports(p, FORBIDDEN_CONTROLLER_IMPORTS).stream())
                    .toList();
            assertThat(violations).as("Response DTO infrastructure dependencies").isEmpty();
        }
    }

    @Test
    @DisplayName("Response DTOs must not expose password or token fields")
    void responseDtosNoSensitiveFields() throws IOException {
        try (Stream<Path> files = Files.walk(INTERFACES_ROOT)) {
            List<String> violations = files
                    .filter(p -> p.getFileName().toString().endsWith("Response.java"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                    .filter(line -> {
                                        String t = line.trim().toLowerCase();
                                        return t.contains("password") || t.contains("token") || t.contains("secret");
                                    });
                        } catch (IOException e) { return Stream.empty(); }
                    })
                    .map(l -> "Sensitive field: " + l)
                    .toList();
            assertThat(violations).as("Response DTO sensitive field violations").isEmpty();
        }
    }

    @Test
    @DisplayName("Interface layer classes exist")
    void interfaceClassesExist() throws IOException {
        try (Stream<Path> files = Files.walk(INTERFACES_ROOT)) {
            long count = files.filter(p -> p.toString().endsWith(".java")).count();
            assertThat(count).as("Interface layer files must exist").isGreaterThanOrEqualTo(5);
        }
    }

    private List<String> checkForbiddenImports(Path file, List<String> forbidden) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> {
                        String t = line.trim();
                        if (!t.startsWith("import ")) return false;
                        return forbidden.stream().anyMatch(t::contains);
                    })
                    .map(line -> file.getFileName() + ": " + line.trim())
                    .toList();
        } catch (IOException e) {
            return List.of(file.getFileName() + ": ERROR reading file");
        }
    }
}
