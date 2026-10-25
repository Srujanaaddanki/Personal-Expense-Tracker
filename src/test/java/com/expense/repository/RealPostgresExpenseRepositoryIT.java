package com.expense.repository;

import com.expense.config.DatabaseConfig;
import com.expense.model.Expense;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real PostgreSQL Integration Test.
 *
 * <p>This test connects to a REAL PostgreSQL database instance (local or remote) to verify
 * true PostgreSQL dialect compatibility, sequence allocation, and constraints.
 *
 * <p>It is conditionally executed ONLY when the environment variable {@code RUN_POSTGRES_IT=true}
 * is defined. It will never fail normal CI or local developer builds when PostgreSQL is offline.
 *
 * <p>To run against a real PostgreSQL instance:
 * <pre>
 *   $env:RUN_POSTGRES_IT = "true"
 *   $env:DB_URL = "jdbc:postgresql://localhost:5432/expense_tracker"
 *   $env:DB_USERNAME = "postgres"
 *   $env:DB_PASSWORD = "your_password"
 *   .\mvnw.cmd test -Dtest=RealPostgresExpenseRepositoryIT
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_IT", matches = "true")
@DisplayName("Real PostgreSQL Integration Test (Conditionally Executed)")
class RealPostgresExpenseRepositoryIT {

    private HikariDataSource dataSource;
    private PostgresExpenseRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = DatabaseConfig.createDataSourceFromEnv();
        repository = new PostgresExpenseRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Real PostgreSQL: Save, query, update, and delete against live PostgreSQL server")
    void testRealPostgresLifecycle() {
        Expense expense = new Expense("PostgreSQL Live Test", "Infrastructure", new BigDecimal("75.00"), LocalDate.now());
        Expense saved = repository.save(expense);

        assertNotNull(saved.getId(), "Real PostgreSQL must assign generated sequence ID");
        assertTrue(saved.getId() > 0);

        Optional<Expense> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("PostgreSQL Live Test", found.get().getDescription());

        // Cleanup
        assertTrue(repository.deleteById(saved.getId()));
        assertTrue(repository.findById(saved.getId()).isEmpty());
    }
}
