package com.expense.repository;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import com.expense.service.ExpenseService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for {@link PostgresExpenseRepository}.
 *
 * <p>Uses an in-memory H2 database configured in PostgreSQL compatibility mode
 * ({@code MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE}) to verify SQL schemas, sequences,
 * and JDBC operations fast and deterministically without requiring an external PostgreSQL instance.
 *
 * <p><b>Note:</b> While H2 in PostgreSQL mode provides high-fidelity SQL dialect and sequence
 * validation for CI/local builds, production deployments should also run validation against
 * a real PostgreSQL server via testcontainers or external staging database.
 */
@DisplayName("PostgresExpenseRepository Integration Tests (PostgreSQL Mode)")
class PostgresExpenseRepositoryTest {

    private HikariDataSource dataSource;
    private PostgresExpenseRepository repository;

    @BeforeEach
    void setUp() {
        String dbName = "testdb_" + UUID.randomUUID().toString().replace("-", "");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(5);

        dataSource = new HikariDataSource(config);
        repository = new PostgresExpenseRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("Insert and find: Persists expense with database-generated ID and retrieves it")
    void testSaveAndFindById() {
        Expense expense = new Expense("Groceries", "Food", new BigDecimal("45.50"), LocalDate.of(2026, 10, 25));

        Expense saved = repository.save(expense);
        assertNotNull(saved);
        assertNotNull(saved.getId(), "Database must generate and return an ID");
        assertTrue(saved.getId() > 0);
        assertEquals("Groceries", saved.getDescription());
        assertEquals("Food", saved.getCategory());
        assertEquals(new BigDecimal("45.50"), saved.getAmount());
        assertEquals(LocalDate.of(2026, 10, 25), saved.getDate());

        Optional<Expense> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals("Groceries", found.get().getDescription());
        assertEquals(new BigDecimal("45.50"), found.get().getAmount());
    }

    @Test
    @DisplayName("Find all: Retrieves multiple persisted records in ascending ID order")
    void testFindAll() {
        repository.save(new Expense("Item 1", "Category A", new BigDecimal("10.00"), LocalDate.now()));
        repository.save(new Expense("Item 2", "Category B", new BigDecimal("20.00"), LocalDate.now()));
        repository.save(new Expense("Item 3", "Category A", new BigDecimal("30.00"), LocalDate.now()));

        List<Expense> all = repository.findAll();
        assertEquals(3, all.size());
        assertTrue(all.get(0).getId() < all.get(1).getId());
        assertTrue(all.get(1).getId() < all.get(2).getId());
    }

    @Test
    @DisplayName("Update: Modifies an existing persisted expense in the database")
    void testUpdateExistingExpense() {
        Expense initial = repository.save(new Expense("Old Description", "Old Cat", new BigDecimal("15.00"), LocalDate.of(2026, 10, 1)));

        Expense toUpdate = new Expense(initial.getId(), "Updated Description", "Updated Cat", new BigDecimal("25.50"), LocalDate.of(2026, 10, 20));
        Optional<Expense> updated = repository.update(toUpdate);

        assertTrue(updated.isPresent());
        assertEquals("Updated Description", updated.get().getDescription());
        assertEquals("Updated Cat", updated.get().getCategory());
        assertEquals(new BigDecimal("25.50"), updated.get().getAmount());

        Optional<Expense> reloaded = repository.findById(initial.getId());
        assertTrue(reloaded.isPresent());
        assertEquals("Updated Description", reloaded.get().getDescription());
        assertEquals(new BigDecimal("25.50"), reloaded.get().getAmount());
    }

    @Test
    @DisplayName("Update non-existent: Returns empty Optional when updating non-existent ID")
    void testUpdateNonExistentReturnsEmpty() {
        Expense nonExistent = new Expense(9999L, "Ghost", "Misc", BigDecimal.TEN, LocalDate.now());
        Optional<Expense> result = repository.update(nonExistent);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Delete: Successfully deletes an existing expense by ID")
    void testDeleteExistingExpense() {
        Expense saved = repository.save(new Expense("To Delete", "Misc", BigDecimal.ONE, LocalDate.now()));
        assertTrue(repository.findById(saved.getId()).isPresent());

        boolean deleted = repository.deleteById(saved.getId());
        assertTrue(deleted);
        assertTrue(repository.findById(saved.getId()).isEmpty());
    }

    @Test
    @DisplayName("Delete non-existent: Returns false when deleting non-existent ID")
    void testDeleteNonExistentReturnsFalse() {
        boolean deleted = repository.deleteById(8888L);
        assertFalse(deleted);
    }

    @Test
    @DisplayName("Persistence across restarts: New repository instance against same database reads previous data")
    void testPersistenceAcrossRestarts() {
        // Session 1: save two records
        repository.save(new Expense("Coffee", "Food", new BigDecimal("4.50"), LocalDate.now()));
        repository.save(new Expense("Metro", "Transport", new BigDecimal("2.75"), LocalDate.now()));

        // Session 2: instantiate a fresh repository pointing to the same database
        PostgresExpenseRepository restartedRepo = new PostgresExpenseRepository(dataSource);
        List<Expense> loaded = restartedRepo.findAll();

        assertEquals(2, loaded.size());
        assertEquals("Coffee", loaded.get(0).getDescription());
        assertEquals("Metro", loaded.get(1).getDescription());
    }

    @Test
    @DisplayName("ExpenseService integration: Service seamlessly performs full lifecycle with PostgreSQL repository")
    void testExpenseServiceLifecycleWithPostgres() {
        ExpenseService service = new ExpenseService(repository);
        service.loadFromStorage();
        assertEquals(0, service.getExpenseCount());

        // Add
        Expense created = service.addExpense("Headphones", "Electronics", new BigDecimal("99.99"), LocalDate.now());
        assertNotNull(created.getId());
        assertEquals(1, service.getExpenseCount());

        // View All
        List<Expense> all = service.getAllExpenses();
        assertEquals(1, all.size());
        assertEquals(created.getId(), all.get(0).getId());

        // Update
        Optional<Expense> updated = service.updateExpense(created.getId(), "Wireless Headphones", "Electronics", new BigDecimal("129.99"), LocalDate.now());
        assertTrue(updated.isPresent());
        assertEquals("Wireless Headphones", updated.get().getDescription());

        // Direct DB verification
        Optional<Expense> dbRecord = repository.findById(created.getId());
        assertTrue(dbRecord.isPresent());
        assertEquals("Wireless Headphones", dbRecord.get().getDescription());
        assertEquals(new BigDecimal("129.99"), dbRecord.get().getAmount());

        // Delete
        boolean deleted = service.deleteExpense(created.getId());
        assertTrue(deleted);
        assertEquals(0, service.getExpenseCount());
        assertTrue(repository.findById(created.getId()).isEmpty());
    }

    @Test
    @DisplayName("Storage location: Returns active JDBC URL description")
    void testStorageLocation() {
        String location = repository.getStorageLocation();
        assertNotNull(location);
        assertTrue(location.contains("jdbc:h2:mem:"));
    }
}
