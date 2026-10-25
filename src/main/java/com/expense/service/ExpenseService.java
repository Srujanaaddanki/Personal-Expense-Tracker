package com.expense.service;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import com.expense.storage.CsvExpenseStorage;
import com.expense.util.InputValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

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
    private final CsvExpenseStorage storage;
    private final Path dataFilePath;

    /**
     * Initializes an in-memory expense service without persistence.
     */
    public ExpenseService() {
        this(null, null);
    }

    /**
     * Initializes an expense service with automated CSV persistence.
     *
     * @param storage Storage handler for reading and writing CSV files
     * @param dataFilePath Path to the target CSV persistence file
     */
    public ExpenseService(CsvExpenseStorage storage, Path dataFilePath) {
        this.expenses = new ArrayList<>();
        this.nextId = 1L;
        this.storage = storage;
        this.dataFilePath = dataFilePath;
    }

    /**
     * Bulk loads a validated list of expenses into the service, preserving original IDs
     * and safely updating the sequential ID counter to prevent future ID collisions.
     *
     * <p>Validates all entities upfront before mutating service state. If validation fails,
     * existing in-memory expenses and counter remain completely untouched.
     *
     * @param loadedExpenses List of expenses to load
     * @throws IllegalArgumentException if the list is null, contains null elements, duplicate IDs, or invalid fields
     * @throws IllegalStateException if the maximum ID in the list reaches {@link Long#MAX_VALUE}
     */
    public void loadExpenses(List<Expense> loadedExpenses) {
        if (loadedExpenses == null) {
            throw new IllegalArgumentException("Loaded expenses list cannot be null.");
        }

        long maxId = 0L;
        Set<Long> seenIds = new HashSet<>();
        for (Expense e : loadedExpenses) {
            if (e == null) {
                throw new IllegalArgumentException("Loaded expense cannot be null.");
            }
            if (e.getId() == null || e.getId() <= 0) {
                throw new IllegalArgumentException("Expense ID must be a positive number.");
            }
            if (!seenIds.add(e.getId())) {
                throw new IllegalArgumentException("Duplicate expense ID found: " + e.getId());
            }
            InputValidator.validateDescription(e.getDescription());
            InputValidator.validateCategory(e.getCategory());
            InputValidator.validateAmount(e.getAmount());
            InputValidator.validateDate(e.getDate());

            if (e.getId() > maxId) {
                maxId = e.getId();
            }
        }

        if (maxId == Long.MAX_VALUE) {
            throw new IllegalStateException("Expense ID limit reached (Long.MAX_VALUE). Cannot generate further IDs.");
        }

        this.expenses.clear();
        for (Expense e : loadedExpenses) {
            this.expenses.add(copyExpense(e));
        }
        this.nextId = maxId + 1;
    }

    /**
     * Loads existing expenses from the configured storage file if persistence is enabled.
     *
     * @throws PersistenceException if loading fails due to corrupt syntax or I/O failure
     */
    public void loadFromStorage() {
        if (isPersistenceEnabled()) {
            try {
                List<Expense> loaded = storage.loadExpenses(dataFilePath);
                loadExpenses(loaded);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new PersistenceException("Failed to load expenses from storage: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates and records a new expense with an auto-incremented ID.
     *
     * <p>If persistence is enabled, the new expense is persisted immediately.
     * If persistence fails, the in-memory state is rolled back to prevent inconsistency.
     *
     * @param description Brief description of the expense
     * @param category    Expense category
     * @param amount      Monetary amount (> 0)
     * @param date        Date the expense was incurred
     * @return A detached {@link Expense} copy with an assigned ID
     * @throws IllegalArgumentException if any field is invalid
     * @throws IllegalStateException if nextId has reached {@link Long#MAX_VALUE}
     * @throws PersistenceException if persistence fails
     */
    public Expense addExpense(String description, String category, BigDecimal amount, LocalDate date) {
        InputValidator.validateDescription(description);
        InputValidator.validateCategory(category);
        InputValidator.validateAmount(amount);
        InputValidator.validateDate(date);

        if (nextId == Long.MAX_VALUE) {
            throw new IllegalStateException("Expense ID limit reached (Long.MAX_VALUE). Cannot add more expenses.");
        }

        long allocatedId = nextId;
        Expense expense = new Expense(allocatedId, description.trim(), category.trim(), amount, date);
        expenses.add(expense);
        nextId++;

        if (isPersistenceEnabled()) {
            try {
                storage.saveExpenses(this.expenses, dataFilePath);
            } catch (PersistenceException e) {
                // Transactional rollback: revert in-memory addition and restore ID counter
                expenses.remove(expenses.size() - 1);
                nextId = allocatedId;
                throw e;
            }
        }

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
     * Returns a sorted list of expenses ordered by monetary amount.
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Comparator Strategy:</b> Uses {@link Comparator#comparing(java.util.function.Function)}
     *       with {@link Expense#getAmount}, which delegates to {@link BigDecimal#compareTo(BigDecimal)}.
     *       Ties in amount are resolved deterministically by {@link Expense#getId} in ascending order
     *       for both sorting directions.</li>
     *   <li><b>Non-destructive Sorting:</b> A separate {@link ArrayList} of detached copies is sorted.
     *       The internal {@code expenses} list remains in original insertion order.</li>
     *   <li><b>Algorithm:</b> Java TimSort (adaptive, stable hybrid of Merge Sort and Insertion Sort).</li>
     *   <li><b>Time Complexity:</b> \(O(N \log N)\) average and worst-case; \(O(N)\) best-case.</li>
     *   <li><b>Space Complexity:</b> \(O(N)\) for the detached copies and temporary merge buffer.</li>
     * </ul>
     *
     * @param ascending {@code true} for lowest-to-highest amount; {@code false} for highest-to-lowest
     * @return An unmodifiable list containing detached copies sorted by amount
     */
    public List<Expense> getExpensesSortedByAmount(boolean ascending) {
        List<Expense> sorted = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) {
            sorted.add(copyExpense(expense));
        }

        Comparator<Expense> comparator;
        if (ascending) {
            comparator = Comparator.comparing(Expense::getAmount)
                                   .thenComparing(Expense::getId);
        } else {
            comparator = Comparator.comparing(Expense::getAmount, Comparator.reverseOrder())
                                   .thenComparing(Expense::getId);
        }

        sorted.sort(comparator);
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Returns a sorted list of expenses ordered by date incurred.
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Comparator Strategy:</b> Uses {@link Comparator#comparing(java.util.function.Function)}
     *       with {@link Expense#getDate}. Ties in date are resolved deterministically by
     *       {@link Expense#getId} in ascending order for both directions.</li>
     *   <li><b>Non-destructive Sorting:</b> Allocates a detached copy list without modifying internal state.</li>
     *   <li><b>Algorithm:</b> Java TimSort.</li>
     *   <li><b>Time Complexity:</b> \(O(N \log N)\) average and worst-case.</li>
     *   <li><b>Space Complexity:</b> \(O(N)\).</li>
     * </ul>
     *
     * @param ascending {@code true} for oldest-to-newest date; {@code false} for newest-to-oldest
     * @return An unmodifiable list containing detached copies sorted by date
     */
    public List<Expense> getExpensesSortedByDate(boolean ascending) {
        List<Expense> sorted = new ArrayList<>(expenses.size());
        for (Expense expense : expenses) {
            sorted.add(copyExpense(expense));
        }

        Comparator<Expense> comparator;
        if (ascending) {
            comparator = Comparator.comparing(Expense::getDate)
                                   .thenComparing(Expense::getId);
        } else {
            comparator = Comparator.comparing(Expense::getDate, Comparator.reverseOrder())
                                   .thenComparing(Expense::getId);
        }

        sorted.sort(comparator);
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Retrieves the top K highest-value expenses using a bounded Min-Heap (PriorityQueue).
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Problem:</b> Identify the \(K\) largest expenses from a stream/collection of size \(N\).</li>
     *   <li><b>Algorithm:</b> Bounded Min-Heap of capacity \(K\).
     *       We iterate through all expenses, inserting each into a min-heap.
     *       Whenever the heap size exceeds \(K\), the minimum element is evicted via {@link PriorityQueue#poll()}.
     *       At the end of the iteration, the heap retains precisely the \(K\) largest elements.</li>
     *   <li><b>Comparator &amp; Deterministic Tie-Breaking:</b>
     *       In the min-heap, the element considered "smallest" sits at the root and is evicted first.
     *       When monetary amounts are equal, expenses with <i>higher</i> IDs have lower priority
     *       and must be evicted first so that expenses with <i>lower</i> IDs are retained in the top \(K\).
     *       Therefore, the min-heap comparator orders primarily by amount ascending, and secondarily
     *       by ID in reverse (descending) order:
     *       {@code Comparator.comparing(Expense::getAmount).thenComparing(Expense::getId, Comparator.reverseOrder())}.</li>
     *   <li><b>Final Output Ordering:</b> The retained elements are extracted and sorted in presentation order:
     *       Amount descending, then ID ascending:
     *       {@code Comparator.comparing(Expense::getAmount, Comparator.reverseOrder()).thenComparing(Expense::getId)}.</li>
     *   <li><b>Boundary &amp; Edge Conditions:</b>
     *       <ul>
     *         <li>If \(K \le 0\), throws {@link IllegalArgumentException}.</li>
     *         <li>If the collection is empty, returns an empty unmodifiable list.</li>
     *         <li>If \(K \ge N\), delegates directly to {@link #getExpensesSortedByAmount(boolean)} (descending)
     *             to return all expenses in \(O(N \log N)\) without heap overhead.</li>
     *       </ul>
     *   </li>
     *   <li><b>Time Complexity:</b> \(O(N \log K)\) for \(K &lt; N\), where each insertion/eviction into the heap
     *       takes \(O(\log K)\). Sorting the \(K\) extracted elements takes \(O(K \log K)\).
     *       When \(K \ll N\), this is strictly superior to full sorting \(O(N \log N)\).</li>
     *   <li><b>Space Complexity:</b> \(O(K)\) auxiliary memory for the heap and detached result list.</li>
     * </ul>
     *
     * @param k The number of top expenses to return (\(k &gt; 0\))
     * @return An unmodifiable list containing detached copies of the top \(K\) expenses in descending order
     * @throws IllegalArgumentException if \(k \le 0\)
     */
    public List<Expense> getTopKExpenses(int k) {
        InputValidator.validateK(k);

        if (expenses.isEmpty()) {
            return Collections.emptyList();
        }

        if (k >= expenses.size()) {
            return getExpensesSortedByAmount(false);
        }

        Comparator<Expense> minHeapComparator = Comparator
                .comparing(Expense::getAmount)
                .thenComparing(Expense::getId, Comparator.reverseOrder());

        PriorityQueue<Expense> minHeap = new PriorityQueue<>(k + 1, minHeapComparator);

        for (Expense expense : expenses) {
            minHeap.offer(copyExpense(expense));
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }

        List<Expense> result = new ArrayList<>(minHeap);
        result.sort(Comparator.comparing(Expense::getAmount, Comparator.reverseOrder())
                              .thenComparing(Expense::getId));

        return Collections.unmodifiableList(result);
    }

    /**
     * Updates an existing expense by ID.
     *
     * <p>If persistence is enabled, changes are persisted immediately.
     * If persistence fails, changes are rolled back in memory to preserve consistency.
     *
     * @param id          ID of the expense to update
     * @param description New description
     * @param category    New category
     * @param amount      New amount
     * @param date        New date
     * @return An {@link Optional} containing a detached copy of the updated Expense, or empty if ID does not exist
     * @throws IllegalArgumentException if any argument is invalid
     * @throws PersistenceException if persistence fails
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
        String prevDesc = existing.getDescription();
        String prevCat = existing.getCategory();
        BigDecimal prevAmount = existing.getAmount();
        LocalDate prevDate = existing.getDate();

        existing.setDescription(description.trim());
        existing.setCategory(category.trim());
        existing.setAmount(amount);
        existing.setDate(date);

        if (isPersistenceEnabled()) {
            try {
                storage.saveExpenses(this.expenses, dataFilePath);
            } catch (PersistenceException e) {
                // Transactional rollback: restore original values on failure
                existing.setDescription(prevDesc);
                existing.setCategory(prevCat);
                existing.setAmount(prevAmount);
                existing.setDate(prevDate);
                throw e;
            }
        }

        return Optional.of(copyExpense(existing));
    }

    /**
     * Deletes an expense by its ID.
     * 
     * <p>Time Complexity: \(O(N)\) to find and remove the element from the ArrayList.
     * <p>If persistence is enabled and fails, the deleted entity is restored in memory.
     *
     * @param id ID of the expense to delete
     * @return {@code true} if an expense was found and removed; {@code false} otherwise
     * @throws IllegalArgumentException if the ID is null or non-positive
     * @throws PersistenceException if persistence fails
     */
    public boolean deleteExpense(Long id) {
        InputValidator.validateId(id);

        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId().equals(id)) {
                Expense removed = expenses.remove(i);
                if (isPersistenceEnabled()) {
                    try {
                        storage.saveExpenses(this.expenses, dataFilePath);
                    } catch (PersistenceException e) {
                        // Transactional rollback: re-insert removed entity at original index
                        expenses.add(i, removed);
                        throw e;
                    }
                }
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
     * Aggregates total spending for each category using a hash map.
     *
     * <p><b>DSA Analysis:</b>
     * <ul>
     *   <li><b>Problem:</b> Accumulate monetary amounts grouped by category in a single pass.</li>
     *   <li><b>Data Structure:</b> {@link java.util.HashMap}. Provides average \(O(1)\) lookup
     *       and insertion per category key.</li>
     *   <li><b>Case-Normalization Strategy:</b> Since category search is case-insensitive, spending
     *       is aggregated case-insensitively (e.g. "Food", "food", "FOOD" merge into a single category).
     *       Surrounding whitespace is trimmed. To ensure human-readable output, the first-encountered
     *       display casing is preserved as the map key.</li>
     *   <li><b>Time Complexity:</b> \(O(N)\) single pass over all \(N\) expenses.</li>
     *   <li><b>Space Complexity:</b> \(O(K)\) where \(K\) is the count of distinct categories (\(K \le N\)).</li>
     * </ul>
     *
     * @return An unmodifiable {@link Map} mapping each category to its aggregated total spending
     */
    public Map<String, BigDecimal> calculateCategorySpending() {
        if (expenses.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> normalizedToDisplay = new HashMap<>();
        Map<String, BigDecimal> categoryTotals = new HashMap<>();

        for (Expense expense : expenses) {
            String rawCategory = expense.getCategory().trim();
            String normalizedKey = rawCategory.toLowerCase(Locale.ROOT);

            // Preserve the first-seen readable label for display
            String displayKey = normalizedToDisplay.computeIfAbsent(normalizedKey, k -> rawCategory);

            // Accumulate using immutable BigDecimal addition
            categoryTotals.merge(displayKey, expense.getAmount(), BigDecimal::add);
        }

        return Collections.unmodifiableMap(categoryTotals);
    }

    /**
     * Calculates the percentage of total spending for each category.
     *
     * <p>Formula:
     * \[\text{percentage} = \frac{\text{categoryTotal} \times 100}{\text{grandTotal}}\]
     * Rounded to 2 decimal places using {@link RoundingMode#HALF_UP}.
     *
     * <p>Zero-total handling: If {@code grandTotal} is zero (or empty), every category maps to 0.00%.
     *
     * @param categoryTotals Map of category totals
     * @param grandTotal     Sum total of all expenses
     * @return An unmodifiable map of category names to their percentage of total spending
     */
    public Map<String, BigDecimal> calculateCategoryPercentages(Map<String, BigDecimal> categoryTotals, BigDecimal grandTotal) {
        if (categoryTotals == null || categoryTotals.isEmpty() || grandTotal == null || grandTotal.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, BigDecimal> zeroMap = new HashMap<>();
            if (categoryTotals != null) {
                for (String cat : categoryTotals.keySet()) {
                    zeroMap.put(cat, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                }
            }
            return Collections.unmodifiableMap(zeroMap);
        }

        Map<String, BigDecimal> percentages = new HashMap<>();
        BigDecimal hundred = new BigDecimal("100");

        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            BigDecimal catTotal = entry.getValue();
            BigDecimal pct = catTotal.multiply(hundred)
                                     .divide(grandTotal, 2, RoundingMode.HALF_UP);
            percentages.put(entry.getKey(), pct);
        }

        return Collections.unmodifiableMap(percentages);
    }

    /**
     * Returns the count of expenses currently held in memory.
     *
     * @return Number of expenses
     */
    public int getExpenseCount() {
        return expenses.size();
    }

    /**
     * Checks if automated CSV persistence is active for this service instance.
     *
     * @return {@code true} if storage and dataFilePath are configured; {@code false} otherwise
     */
    public boolean isPersistenceEnabled() {
        return storage != null && dataFilePath != null;
    }

    /**
     * Gets the configured target CSV file path.
     *
     * @return Target file path, or {@code null} if running in in-memory mode
     */
    public Path getDataFilePath() {
        return dataFilePath;
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
