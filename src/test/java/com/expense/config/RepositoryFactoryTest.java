package com.expense.config;

import com.expense.repository.CsvExpenseRepository;
import com.expense.repository.ExpenseRepository;
import com.expense.repository.PostgresExpenseRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RepositoryFactory Configuration Tests")
class RepositoryFactoryTest {

    @Test
    @DisplayName("Default selection: Produces CsvExpenseRepository when STORAGE_TYPE is csv or null")
    void testDefaultStorageIsCsv() {
        ExpenseRepository csvRepo = RepositoryFactory.createRepository(null);
        assertTrue(csvRepo instanceof CsvExpenseRepository);

        ExpenseRepository explicitCsv = RepositoryFactory.createRepository("csv");
        assertTrue(explicitCsv instanceof CsvExpenseRepository);
    }

    @Test
    @DisplayName("Fallback selection: Unrecognized storage type falls back safely to CSV")
    void testFallbackToCsvOnUnrecognizedType() {
        ExpenseRepository fallback = RepositoryFactory.createRepository("non_existent_engine");
        assertTrue(fallback instanceof CsvExpenseRepository);
    }

    @Test
    @DisplayName("Custom CSV path: createCsvRepository configures target file path correctly")
    void testCreateCsvRepositoryCustomPath() {
        Path customPath = Path.of("custom_dir", "my_expenses.csv");
        CsvExpenseRepository repo = RepositoryFactory.createCsvRepository(customPath);
        assertNotNull(repo);
        assertEquals(customPath, repo.getDataFilePath());
    }

    @Test
    @DisplayName("PostgreSQL factory: createPostgresRepository properly wraps supplied DataSource")
    void testCreatePostgresRepository() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:factory_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");

        HikariDataSource ds = new HikariDataSource(config);
        try {
            PostgresExpenseRepository repo = RepositoryFactory.createPostgresRepository(ds);
            assertNotNull(repo);
            assertNotNull(repo.getDataSource());
        } finally {
            ds.close();
        }
    }
}
