package com.expense.repository;

import com.expense.model.Expense;

import java.util.List;
import java.util.Optional;

/**
 * Storage-agnostic repository interface for expense persistence.
 *
 * <p>Decouples higher-level domain services from underlying persistence engines
 * (CSV files, relational databases, etc.) adhering to the Dependency Inversion Principle (DIP).
 */
public interface ExpenseRepository {

    /**
     * Loads all persisted expenses in deterministic ascending ID order.
     *
     * @return List of persisted expenses, or an empty list if no records exist
     * @throws com.expense.exception.PersistenceException if an I/O or storage error occurs
     */
    List<Expense> findAll();

    /**
     * Finds a persisted expense by its unique identifier.
     *
     * @param id Identifier of the expense to locate
     * @return An {@link Optional} containing the found expense, or empty if not found
     * @throws com.expense.exception.PersistenceException if storage query fails
     */
    Optional<Expense> findById(Long id);

    /**
     * Persists a new expense.
     *
     * <p>For databases with automated sequence/serial key generation, the returned entity
     * contains the database-assigned identifier.
     *
     * @param expense Expense to persist
     * @return The persisted expense entity with its assigned ID
     * @throws com.expense.exception.PersistenceException if persistence fails
     */
    Expense save(Expense expense);

    /**
     * Updates an existing persisted expense with matching ID.
     *
     * @param expense Updated expense entity
     * @return An {@link Optional} containing the updated entity, or empty if ID does not exist
     * @throws com.expense.exception.PersistenceException if persistence fails
     */
    Optional<Expense> update(Expense expense);

    /**
     * Deletes a persisted expense by its unique identifier.
     *
     * @param id Identifier of the expense to delete
     * @return {@code true} if an existing record was deleted; {@code false} if ID did not exist
     * @throws com.expense.exception.PersistenceException if deletion fails
     */
    boolean deleteById(Long id);

    /**
     * Returns a human-readable description of the storage destination (file path, JDBC URL, etc.).
     *
     * @return Storage target description
     */
    String getStorageLocation();
}
