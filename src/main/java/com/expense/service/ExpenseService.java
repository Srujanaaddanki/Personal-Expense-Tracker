package com.expense.service;

import com.expense.model.Expense;
import com.expense.util.InputValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service class handling core expense business logic.
 * 
 * <p>Data Structure Design:
 * <ul>
 *   <li><b>ArrayList:</b> Used for in-memory storage of expenses.
 *       Provides \(O(1)\) amortized insertion at the end and predictable iteration order.
 *       Elements are stored in contiguous memory, offering excellent CPU cache locality.</li>
 *   <li><b>Linear Search:</b> Used in {@link #getExpenseById(Long)} because expenses in the list
 *       are not indexed by a hash map or tree yet. Searching takes \(O(N)\) time in the worst case.</li>
 *   <li><b>Defensive Deep Copying:</b> {@link #getAllExpenses()} and {@link #getExpenseById(Long)}
 *       return detached object copies within an unmodifiable list so callers cannot mutate internal
 *       service state directly.</li>
 * </ul>
 */
public class ExpenseService {

    private final List<Expense> expenses;
    private long nextId;

    /**
     * Initializes an empty in-memory expense service.
     */
    public ExpenseService() {
        this.expenses = new ArrayList<>();
        this.nextId = 1L;
    }

    /**
     * Creates and records a new expense with an auto-incremented ID.
     *
     * @param description Brief description of the expense
     * @param category    Expense category
     * @param amount      Monetary amount (> 0)
     * @param date        Date the expense was incurred
     * @return A detached {@link Expense} copy with an assigned ID
     * @throws IllegalArgumentException if any field is invalid
     */
    public Expense addExpense(String description, String category, BigDecimal amount, LocalDate date) {
        InputValidator.validateDescription(description);
        InputValidator.validateCategory(category);
        InputValidator.validateAmount(amount);
        InputValidator.validateDate(date);

        Expense expense = new Expense(nextId++, description.trim(), category.trim(), amount, date);
        expenses.add(expense);
        return copyExpense(expense);
    }

    /**
     * Retrieves all recorded expenses as detached copies.
     *
     * @return An unmodifiable view of detached expense copies (defensive deep copy)
     */
    public List<Expense> getAllExpenses() {
        List<Expense> copies = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) {
            copies.add(copyExpense(expense));
        }
        return Collections.unmodifiableList(copies);
    }

    /**
     * Finds an expense by its unique ID using linear search.
     * 
     * <p>Time Complexity: \(O(N)\) where \(N\) is the number of expenses.
     * <p>Space Complexity: \(O(1)\).
     *
     * @param id The unique identifier of the expense
     * @return An {@link Optional} containing a detached copy of the found Expense, or empty if not found
     * @throws IllegalArgumentException if the ID is null or non-positive
     */
    public Optional<Expense> getExpenseById(Long id) {
        InputValidator.validateId(id);
        return findInternalById(id).map(this::copyExpense);
    }

    /**
     * Searches expenses by category (case-insensitive).
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Problem:</b> Locate all expenses belonging to a specified category.</li>
     *   <li><b>Algorithm:</b> Single-pass linear scan filtering through the {@link ArrayList}.
     *       We compare normalized strings using {@link String#equalsIgnoreCase(String)}.</li>
     *   <li><b>Time Complexity:</b> \(O(N)\) where \(N\) is the number of expenses.
     *       We inspect each element exactly once.</li>
     *   <li><b>Space Complexity:</b> \(O(M)\) where \(M\) is the number of matching items (\(M \le N\)),
     *       allocating a new list with detached copies to preserve encapsulation.</li>
     * </ul>
     *
     * @param category Category name to search for
     * @return Unmodifiable list containing detached copies of matching expenses (or empty list if none match)
     * @throws IllegalArgumentException if category is null or blank
     */
    public List<Expense> getExpensesByCategory(String category) {
        InputValidator.validateCategory(category);
        String normalized = category.trim();

        List<Expense> matches = new ArrayList<>();
        for (Expense expense : expenses) {
            if (expense.getCategory().equalsIgnoreCase(normalized)) {
                matches.add(copyExpense(expense));
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Filters expenses within an inclusive date range [startDate, endDate].
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Problem:</b> Select all records whose date falls on or between two calendar dates.</li>
     *   <li><b>Algorithm:</b> Single-pass linear filter. A date \(d\) satisfies the range if:
     *       \(\neg (d < \text{startDate}) \land \neg (d > \text{endDate})\).
     *       In modern Java Date API: {@code !d.isBefore(startDate) && !d.isAfter(endDate)}.</li>
     *   <li><b>Time Complexity:</b> \(O(N)\) where \(N\) is the total number of expenses.</li>
     *   <li><b>Space Complexity:</b> \(O(M)\) where \(M\) is the count of matching expenses.</li>
     * </ul>
     *
     * @param startDate Starting boundary (inclusive)
     * @param endDate   Ending boundary (inclusive)
     * @return Unmodifiable list containing detached copies of matching expenses
     * @throws IllegalArgumentException if either date is null or if startDate is after endDate
     */
    public List<Expense> getExpensesByDateRange(LocalDate startDate, LocalDate endDate) {
        InputValidator.validateDateRange(startDate, endDate);

        List<Expense> matches = new ArrayList<>();
        for (Expense expense : expenses) {
            LocalDate d = expense.getDate();
            if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                matches.add(copyExpense(expense));
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Updates an existing expense by ID.
     *
     * @param id          ID of the expense to update
     * @param description New description
     * @param category    New category
     * @param amount      New amount
     * @param date        New date
     * @return An {@link Optional} containing a detached copy of the updated Expense, or empty if ID does not exist
     * @throws IllegalArgumentException if any argument is invalid
     */
    public Optional<Expense> updateExpense(Long id, String description, String category, BigDecimal amount, LocalDate date) {
        InputValidator.validateId(id);
        InputValidator.validateDescription(description);
        InputValidator.validateCategory(category);
        InputValidator.validateAmount(amount);
        InputValidator.validateDate(date);

        Optional<Expense> existingOpt = findInternalById(id);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }

        Expense existing = existingOpt.get();
        existing.setDescription(description.trim());
        existing.setCategory(category.trim());
        existing.setAmount(amount);
        existing.setDate(date);

        return Optional.of(copyExpense(existing));
    }

    /**
     * Deletes an expense by its ID.
     * 
     * <p>Time Complexity: \(O(N)\) to find and remove the element from the ArrayList.
     *
     * @param id ID of the expense to delete
     * @return {@code true} if an expense was found and removed; {@code false} otherwise
     * @throws IllegalArgumentException if the ID is null or non-positive
     */
    public boolean deleteExpense(Long id) {
        InputValidator.validateId(id);

        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId().equals(id)) {
                expenses.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the sum total of all recorded expenses.
     * 
     * <p>Note: Because {@link BigDecimal} is immutable, operations like {@code add()}
     * return a new instance rather than modifying the current one.
     * 
     * <p>Time Complexity: \(O(N)\) single pass over all items.
     * <p>Space Complexity: \(O(1)\).
     *
     * @return Total spending as a {@link BigDecimal}, or {@link BigDecimal#ZERO} if empty
     */
    public BigDecimal calculateTotalSpending() {
        BigDecimal total = BigDecimal.ZERO;
        for (Expense expense : expenses) {
            total = total.add(expense.getAmount());
        }
        return total;
    }

    /**
     * Returns the count of expenses currently held in memory.
     *
     * @return Number of expenses
     */
    public int getExpenseCount() {
        return expenses.size();
    }

    // --- Private Helper Methods ---

    /**
     * Finds the actual stored entity reference for internal service mutations.
     */
    private Optional<Expense> findInternalById(Long id) {
        for (Expense expense : expenses) {
            if (expense.getId().equals(id)) {
                return Optional.of(expense);
            }
        }
        return Optional.empty();
    }

    /**
     * Creates a detached deep copy of an expense to protect encapsulation.
     */
    private Expense copyExpense(Expense original) {
        return new Expense(
                original.getId(),
                original.getDescription(),
                original.getCategory(),
                original.getAmount(),
                original.getDate()
        );
    }
}
