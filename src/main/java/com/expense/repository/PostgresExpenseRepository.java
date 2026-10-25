package com.expense.repository;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of {@link ExpenseRepository} using Spring JDBC and HikariCP.
 *
 * <p>Key Architecture Highlights:
 * <ul>
 *   <li><b>Database-Generated IDs:</b> Relies on PostgreSQL's sequence generator ({@code BIGSERIAL})
 *       via {@link GeneratedKeyHolder}.</li>
 *   <li><b>Parameterized Queries:</b> Strictly prevents SQL injection using prepared statements.</li>
 *   <li><b>Idempotent Schema Init:</b> Safely executes {@code schema.sql} on startup without dropping tables.</li>
 *   <li><b>Consistent Exception Wrapping:</b> Wraps all {@link DataAccessException} into {@link PersistenceException}.</li>
 * </ul>
 */
public class PostgresExpenseRepository implements ExpenseRepository {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Expense> ROW_MAPPER = (rs, rowNum) -> new Expense(
            rs.getLong("id"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getBigDecimal("amount"),
            rs.getDate("date").toLocalDate()
    );

    public PostgresExpenseRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource cannot be null.");
        }
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        initSchema();
    }

    private void initSchema() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
        } catch (Exception e) {
            throw new PersistenceException("Failed to initialize database schema from schema.sql: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Expense> findAll() {
        try {
            List<Expense> results = jdbcTemplate.query(
                    "SELECT id, description, category, amount, date FROM expenses ORDER BY id ASC",
                    ROW_MAPPER
            );
            return Collections.unmodifiableList(results);
        } catch (DataAccessException e) {
            throw new PersistenceException("Failed to query expenses from PostgreSQL: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Expense> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        try {
            List<Expense> results = jdbcTemplate.query(
                    "SELECT id, description, category, amount, date FROM expenses WHERE id = ?",
                    ROW_MAPPER,
                    id
            );
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (DataAccessException e) {
            throw new PersistenceException("Failed to find expense by ID " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Expense save(Expense expense) {
        if (expense == null) {
            throw new IllegalArgumentException("Expense cannot be null.");
        }

        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO expenses (description, category, amount, date) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, expense.getDescription().trim());
                ps.setString(2, expense.getCategory().trim());
                ps.setBigDecimal(3, expense.getAmount());
                ps.setDate(4, Date.valueOf(expense.getDate()));
                return ps;
            }, keyHolder);

            Number generatedKey = keyHolder.getKey();
            if (generatedKey == null) {
                throw new PersistenceException("Database failed to return a generated ID for the inserted expense.");
            }

            long id = generatedKey.longValue();
            return new Expense(id, expense.getDescription().trim(), expense.getCategory().trim(), expense.getAmount(), expense.getDate());
        } catch (DataAccessException e) {
            throw new PersistenceException("Failed to insert expense into database: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Expense> update(Expense expense) {
        if (expense == null || expense.getId() == null) {
            return Optional.empty();
        }

        try {
            int updatedRows = jdbcTemplate.update(
                    "UPDATE expenses SET description = ?, category = ?, amount = ?, date = ? WHERE id = ?",
                    expense.getDescription().trim(),
                    expense.getCategory().trim(),
                    expense.getAmount(),
                    Date.valueOf(expense.getDate()),
                    expense.getId()
            );

            if (updatedRows == 0) {
                return Optional.empty();
            }

            return Optional.of(new Expense(
                    expense.getId(),
                    expense.getDescription().trim(),
                    expense.getCategory().trim(),
                    expense.getAmount(),
                    expense.getDate()
            ));
        } catch (DataAccessException e) {
            throw new PersistenceException("Failed to update expense with ID " + expense.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null || id <= 0) {
            return false;
        }

        try {
            int deletedRows = jdbcTemplate.update("DELETE FROM expenses WHERE id = ?", id);
            return deletedRows > 0;
        } catch (DataAccessException e) {
            throw new PersistenceException("Failed to delete expense with ID " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String getStorageLocation() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (SQLException e) {
            return "PostgreSQL (URL unavailable)";
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
