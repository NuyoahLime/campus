package com.campusguinness;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared base class for PostgreSQL integration tests.
 * All persistence-layer tests that need a real PostgreSQL database
 * should extend this class to reuse the Testcontainers configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgreSqlIntegrationTestSupport {
}
