package com.expense.service;

import com.expense.model.Expense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExpenseService Unit Tests")
class ExpenseServiceTest {

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService();
    }

    // --- Add Expense Tests ---

    @Test
    @DisplayName("Should successfully add an expense and assign sequential IDs")
    void testAddExpenseSuccess() {
        Expense e1 = expenseService.addExpense("Groceries", "Food", new BigDecimal("55.20"), LocalDate.now());
        Expense e2 = expenseService.addExpense("Bus pass", "Transport", new BigDecimal("30.00"), LocalDate.now());

        assertNotNull(e1);
        assertEquals(1L, e1.getId());
        assertEquals("Groceries", e1.getDescription());
        assertEquals("Food", e1.getCategory());
        assertEquals(new BigDecimal("55.20"), e1.getAmount());

        assertNotNull(e2);
        assertEquals(2L, e2.getId());
        assertEquals(2, expenseService.getExpenseCount());
    }

    @Test
    @DisplayName("Should reject adding expense with null or blank description")
    void testAddExpenseInvalidDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense(null, "Food", new BigDecimal("10.00"), LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("   ", "Food", new BigDecimal("10.00"), LocalDate.now()));
    }

    @Test
    @DisplayName("Should reject adding expense with null or blank category")
    void testAddExpenseInvalidCategory() {
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", null, new BigDecimal("4.00"), LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", "  ", new BigDecimal("4.00"), LocalDate.now()));
    }

    @Test
    @DisplayName("Should reject adding expense with null, zero, or negative amount")
    void testAddExpenseInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", "Food", null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", "Food", BigDecimal.ZERO, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", "Food", new BigDecimal("-4.50"), LocalDate.now()));
    }

    @Test
    @DisplayName("Should reject adding expense with null date")
    void testAddExpenseNullDate() {
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.addExpense("Coffee", "Food", new BigDecimal("4.00"), null));
    }

    // --- Defensive Copying & Encapsulation Tests ---

    @Test
    @DisplayName("Should return all expenses and throw UnsupportedOperationException on add/remove")
    void testGetAllExpensesUnmodifiableList() {
        expenseService.addExpense("Book", "Education", new BigDecimal("20.00"), LocalDate.now());
        expenseService.addExpense("Lunch", "Food", new BigDecimal("12.50"), LocalDate.now());

        List<Expense> all = expenseService.getAllExpenses();
        assertEquals(2, all.size());

        // Modifying the returned list structurally must fail
        assertThrows(UnsupportedOperationException.class, () ->
                all.add(new Expense(99L, "Hack", "Evil", new BigDecimal("100"), LocalDate.now())));
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));
    }

    @Test
    @DisplayName("Defensive deep copy: Mutating an expense from getAllExpenses() does not alter stored entity")
    void testGetAllExpensesDetachedCopies() {
        Expense added = expenseService.addExpense("Book", "Education", new BigDecimal("20.00"), LocalDate.now());

        List<Expense> all = expenseService.getAllExpenses();
        Expense retrieved = all.get(0);

        // Mutate the detached copy
        retrieved.setAmount(new BigDecimal("9999.99"));
        retrieved.setDescription("Tampered Title");

        // Verify that internal state in service remains untouched
        Expense stored = expenseService.getExpenseById(added.getId()).orElseThrow();
        assertEquals(new BigDecimal("20.00"), stored.getAmount(), "Stored amount must remain unchanged");
        assertEquals("Book", stored.getDescription(), "Stored description must remain unchanged");
    }

    @Test
    @DisplayName("Defensive deep copy: Mutating an expense from getExpenseById() does not alter stored entity")
    void testGetExpenseByIdDetachedCopy() {
        Expense added = expenseService.addExpense("Headphones", "Electronics", new BigDecimal("50.00"), LocalDate.now());

        Expense retrieved = expenseService.getExpenseById(added.getId()).orElseThrow();

        // Mutate the detached copy
        retrieved.setAmount(new BigDecimal("1.00"));
        retrieved.setDescription("Tampered");

        // Stored entity must remain untouched
        Expense storedAgain = expenseService.getExpenseById(added.getId()).orElseThrow();
        assertEquals(new BigDecimal("50.00"), storedAgain.getAmount(), "Stored entity must not reflect external mutation");
        assertEquals("Headphones", storedAgain.getDescription());
    }

    // --- Find by ID Tests ---

    @Test
    @DisplayName("Should find an existing expense by its ID")
    void testGetExpenseByIdFound() {
        Expense created = expenseService.addExpense("Keyboard", "Electronics", new BigDecimal("75.00"), LocalDate.now());

        Optional<Expense> found = expenseService.getExpenseById(created.getId());
        assertTrue(found.isPresent());
        assertEquals("Keyboard", found.get().getDescription());
        assertEquals(new BigDecimal("75.00"), found.get().getAmount());
    }

    @Test
    @DisplayName("Should return empty Optional when searching for non-existent ID")
    void testGetExpenseByIdNotFound() {
        expenseService.addExpense("Lunch", "Food", new BigDecimal("10.00"), LocalDate.now());

        Optional<Expense> found = expenseService.getExpenseById(999L);
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should reject searching with null or non-positive ID")
    void testGetExpenseByIdInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpenseById(null));
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpenseById(0L));
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpenseById(-5L));
    }

    // --- Update Expense Tests ---

    @Test
    @DisplayName("Should successfully update an existing expense")
    void testUpdateExpenseSuccess() {
        Expense created = expenseService.addExpense("Cinema", "Entertainment", new BigDecimal("12.00"), LocalDate.now());
        LocalDate newDate = LocalDate.of(2026, 11, 1);

        Optional<Expense> updated = expenseService.updateExpense(
                created.getId(), "IMAX Cinema", "Entertainment", new BigDecimal("18.50"), newDate);

        assertTrue(updated.isPresent());
        assertEquals("IMAX Cinema", updated.get().getDescription());
        assertEquals(new BigDecimal("18.50"), updated.get().getAmount());
        assertEquals(newDate, updated.get().getDate());

        // Verify retrieval confirms updated values
        Expense retrieved = expenseService.getExpenseById(created.getId()).orElseThrow();
        assertEquals("IMAX Cinema", retrieved.getDescription());
        assertEquals(new BigDecimal("18.50"), retrieved.getAmount());
    }

    @Test
    @DisplayName("Should return empty Optional when updating a non-existent ID")
    void testUpdateExpenseNotFound() {
        Optional<Expense> result = expenseService.updateExpense(
                404L, "Ghost", "Other", new BigDecimal("10.00"), LocalDate.now());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should reject updating an expense with invalid inputs")
    void testUpdateExpenseValidationFailure() {
        Expense created = expenseService.addExpense("Snack", "Food", new BigDecimal("5.00"), LocalDate.now());

        assertThrows(IllegalArgumentException.class, () ->
                expenseService.updateExpense(created.getId(), "", "Food", new BigDecimal("5.00"), LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.updateExpense(created.getId(), "Snack", "Food", new BigDecimal("-1.00"), LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () ->
                expenseService.updateExpense(null, "Snack", "Food", new BigDecimal("5.00"), LocalDate.now()));
    }

    // --- Delete Expense Tests ---

    @Test
    @DisplayName("Should successfully delete an existing expense")
    void testDeleteExpenseSuccess() {
        Expense e1 = expenseService.addExpense("Item 1", "Misc", new BigDecimal("10.00"), LocalDate.now());
        Expense e2 = expenseService.addExpense("Item 2", "Misc", new BigDecimal("20.00"), LocalDate.now());

        boolean deleted = expenseService.deleteExpense(e1.getId());
        assertTrue(deleted);
        assertEquals(1, expenseService.getExpenseCount());
        assertTrue(expenseService.getExpenseById(e1.getId()).isEmpty());
        assertTrue(expenseService.getExpenseById(e2.getId()).isPresent());
    }

    @Test
    @DisplayName("Should return false when attempting to delete non-existent ID")
    void testDeleteExpenseNotFound() {
        expenseService.addExpense("Item 1", "Misc", new BigDecimal("10.00"), LocalDate.now());

        boolean deleted = expenseService.deleteExpense(999L);
        assertFalse(deleted);
        assertEquals(1, expenseService.getExpenseCount());
    }

    @Test
    @DisplayName("Should reject deleting with null or non-positive ID")
    void testDeleteExpenseInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> expenseService.deleteExpense(null));
        assertThrows(IllegalArgumentException.class, () -> expenseService.deleteExpense(0L));
        assertThrows(IllegalArgumentException.class, () -> expenseService.deleteExpense(-1L));
    }

    // --- Total Spending Calculation Tests ---

    @Test
    @DisplayName("Should return BigDecimal.ZERO when calculating total for empty expenses")
    void testCalculateTotalSpendingEmpty() {
        assertEquals(BigDecimal.ZERO, expenseService.calculateTotalSpending());
    }

    @Test
    @DisplayName("Should accurately calculate total spending with high precision")
    void testCalculateTotalSpendingWithExpenses() {
        expenseService.addExpense("Groceries", "Food", new BigDecimal("45.25"), LocalDate.now());
        expenseService.addExpense("Electricity", "Utilities", new BigDecimal("60.75"), LocalDate.now());
        expenseService.addExpense("Coffee", "Food", new BigDecimal("4.00"), LocalDate.now());

        BigDecimal total = expenseService.calculateTotalSpending();
        assertEquals(new BigDecimal("110.00"), total);
    }

    // --- Category Search Tests (Milestone 3A) ---

    @Test
    @DisplayName("Category search: Should find matching expenses case-insensitively")
    void testGetExpensesByCategoryMatches() {
        expenseService.addExpense("Burger", "Food", new BigDecimal("12.00"), LocalDate.now());
        expenseService.addExpense("Bus", "Transport", new BigDecimal("3.00"), LocalDate.now());
        expenseService.addExpense("Pizza", "FOOD", new BigDecimal("15.00"), LocalDate.now());
        expenseService.addExpense("Salad", "food", new BigDecimal("8.00"), LocalDate.now());

        List<Expense> foodMatches = expenseService.getExpensesByCategory("Food");
        assertEquals(3, foodMatches.size());

        List<Expense> transportMatches = expenseService.getExpensesByCategory("transport");
        assertEquals(1, transportMatches.size());
        assertEquals("Bus", transportMatches.get(0).getDescription());
    }

    @Test
    @DisplayName("Category search: Should return empty list when no matches exist or on empty collection")
    void testGetExpensesByCategoryNoMatches() {
        // Empty collection
        assertTrue(expenseService.getExpensesByCategory("Food").isEmpty());

        // Non-empty collection, nonexistent category
        expenseService.addExpense("Movie", "Entertainment", new BigDecimal("15.00"), LocalDate.now());
        List<Expense> matches = expenseService.getExpensesByCategory("Health");
        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    @Test
    @DisplayName("Category search: Should reject null or blank category")
    void testGetExpensesByCategoryInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByCategory(null));
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByCategory(""));
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByCategory("   "));
    }

    @Test
    @DisplayName("Category search: Should return detached copies to preserve encapsulation")
    void testGetExpensesByCategoryDefensiveCopy() {
        Expense added = expenseService.addExpense("Book", "Education", new BigDecimal("25.00"), LocalDate.now());

        List<Expense> matches = expenseService.getExpensesByCategory("Education");
        matches.get(0).setAmount(new BigDecimal("999.00"));

        Expense stored = expenseService.getExpenseById(added.getId()).orElseThrow();
        assertEquals(new BigDecimal("25.00"), stored.getAmount(), "Stored amount must not be altered by mutating search result");
    }

    // --- Date-Range Filtering Tests (Milestone 3A) ---

    @Test
    @DisplayName("Date range: Should filter expenses inclusively between start and end dates")
    void testGetExpensesByDateRangeInclusive() {
        LocalDate oct10 = LocalDate.of(2026, 10, 10);
        LocalDate oct15 = LocalDate.of(2026, 10, 15);
        LocalDate oct20 = LocalDate.of(2026, 10, 20);
        LocalDate oct25 = LocalDate.of(2026, 10, 25);
        LocalDate oct30 = LocalDate.of(2026, 10, 30);

        expenseService.addExpense("Before range", "Misc", new BigDecimal("5.00"), oct10);
        Expense boundaryStart = expenseService.addExpense("On start date", "Misc", new BigDecimal("10.00"), oct15);
        Expense middle = expenseService.addExpense("In middle", "Misc", new BigDecimal("20.00"), oct20);
        Expense boundaryEnd = expenseService.addExpense("On end date", "Misc", new BigDecimal("30.00"), oct25);
        expenseService.addExpense("After range", "Misc", new BigDecimal("40.00"), oct30);

        List<Expense> results = expenseService.getExpensesByDateRange(oct15, oct25);

        assertEquals(3, results.size(), "Must include boundary start, middle, and boundary end");
        assertTrue(results.contains(boundaryStart));
        assertTrue(results.contains(middle));
        assertTrue(results.contains(boundaryEnd));
    }

    @Test
    @DisplayName("Date range: Single-day range (start == end) matches only expenses on that specific date")
    void testGetExpensesByDateRangeSingleDay() {
        LocalDate day = LocalDate.of(2026, 10, 20);
        expenseService.addExpense("Day expense 1", "Food", new BigDecimal("10.00"), day);
        expenseService.addExpense("Day expense 2", "Coffee", new BigDecimal("4.00"), day);
        expenseService.addExpense("Other day", "Transport", new BigDecimal("15.00"), day.plusDays(1));

        List<Expense> results = expenseService.getExpensesByDateRange(day, day);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Date range: Should return empty list when no expenses fall in range or on empty collection")
    void testGetExpensesByDateRangeNoMatches() {
        // Empty collection
        LocalDate oct1 = LocalDate.of(2026, 10, 1);
        LocalDate oct31 = LocalDate.of(2026, 10, 31);
        assertTrue(expenseService.getExpensesByDateRange(oct1, oct31).isEmpty());

        // Expenses outside range
        expenseService.addExpense("Summer trip", "Travel", new BigDecimal("500.00"), LocalDate.of(2026, 7, 15));
        List<Expense> results = expenseService.getExpensesByDateRange(oct1, oct31);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Date range: Should reject inverted boundaries (startDate > endDate) or null dates")
    void testGetExpensesByDateRangeInvalidInputs() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        LocalDate end = LocalDate.of(2026, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByDateRange(start, end),
                "Start date after end date must throw IllegalArgumentException");
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByDateRange(null, end));
        assertThrows(IllegalArgumentException.class, () -> expenseService.getExpensesByDateRange(start, null));
    }

    @Test
    @DisplayName("Date range: Should return detached copies to preserve encapsulation")
    void testGetExpensesByDateRangeDefensiveCopy() {
        LocalDate today = LocalDate.now();
        Expense added = expenseService.addExpense("Stationery", "Office", new BigDecimal("14.00"), today);

        List<Expense> results = expenseService.getExpensesByDateRange(today, today);
        results.get(0).setAmount(new BigDecimal("9999.00"));

        Expense stored = expenseService.getExpenseById(added.getId()).orElseThrow();
        assertEquals(new BigDecimal("14.00"), stored.getAmount(), "Stored amount must remain unchanged");
    }
}
