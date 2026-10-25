package com.expense.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Expense Model Unit Tests")
class ExpenseTest {

    @Test
    @DisplayName("Should create Expense with all fields using parameterized constructor")
    void testExpenseCreationWithAllFields() {
        Long id = 1L;
        String description = "Textbooks for Semester";
        String category = "Education";
        BigDecimal amount = new BigDecimal("150.50");
        LocalDate date = LocalDate.of(2026, 10, 25);

        Expense expense = new Expense(id, description, category, amount, date);

        assertEquals(id, expense.getId());
        assertEquals(description, expense.getDescription());
        assertEquals(category, expense.getCategory());
        assertEquals(amount, expense.getAmount());
        assertEquals(date, expense.getDate());
    }

    @Test
    @DisplayName("Should create unpersisted Expense without ID")
    void testExpenseCreationWithoutId() {
        String description = "Metro recharge";
        String category = "Transport";
        BigDecimal amount = new BigDecimal("25.00");
        LocalDate date = LocalDate.of(2026, 10, 24);

        Expense expense = new Expense(description, category, amount, date);

        assertNull(expense.getId(), "Unpersisted expense should have a null ID");
        assertEquals(description, expense.getDescription());
        assertEquals(category, expense.getCategory());
        assertEquals(amount, expense.getAmount());
        assertEquals(date, expense.getDate());
    }

    @Test
    @DisplayName("Should update properties correctly via setters")
    void testSettersAndGetters() {
        Expense expense = new Expense();
        expense.setId(10L);
        expense.setDescription("Lunch");
        expense.setCategory("Food");
        expense.setAmount(new BigDecimal("12.99"));
        expense.setDate(LocalDate.of(2026, 10, 20));

        assertEquals(10L, expense.getId());
        assertEquals("Lunch", expense.getDescription());
        assertEquals("Food", expense.getCategory());
        assertEquals(new BigDecimal("12.99"), expense.getAmount());
        assertEquals(LocalDate.of(2026, 10, 20), expense.getDate());
    }

    @Test
    @DisplayName("Equals: Two expenses with the same ID should be equal (Entity Identity)")
    void testEqualsSameId() {
        Expense expense1 = new Expense(1L, "Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());
        Expense expense2 = new Expense(1L, "Updated Coffee", "Food", new BigDecimal("5.00"), LocalDate.now());

        assertEquals(expense1, expense2, "Entities with identical IDs represent the same persistent record");
    }

    @Test
    @DisplayName("Equals: Two expenses with different IDs should not be equal")
    void testEqualsDifferentId() {
        Expense expense1 = new Expense(1L, "Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());
        Expense expense2 = new Expense(2L, "Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());

        assertNotEquals(expense1, expense2);
    }

    @Test
    @DisplayName("Equals: Unpersisted expenses (ID null) match based on business attributes")
    void testEqualsUnpersistedExpenses() {
        LocalDate now = LocalDate.now();
        Expense expense1 = new Expense("Groceries", "Food", new BigDecimal("55.00"), now);
        Expense expense2 = new Expense("Groceries", "Food", new BigDecimal("55.00"), now);
        Expense different = new Expense("Cinema", "Entertainment", new BigDecimal("15.00"), now);

        assertEquals(expense1, expense2, "Unpersisted expenses with identical attributes should be equal");
        assertNotEquals(expense1, different, "Unpersisted expenses with differing attributes should not be equal");
    }

    @Test
    @DisplayName("HashCode contract: Equal objects must have identical hashCodes")
    void testHashCodeContract() {
        Expense expense1 = new Expense(5L, "Dinner", "Food", new BigDecimal("30.00"), LocalDate.now());
        Expense expense2 = new Expense(5L, "Dinner", "Food", new BigDecimal("30.00"), LocalDate.now());

        assertEquals(expense1, expense2);
        assertEquals(expense1.hashCode(), expense2.hashCode(), "Equal objects must have matching hashCode");
    }

    @Test
    @DisplayName("Set stability: HashCode stability ensures entity is not lost in Set when ID is assigned")
    void testHashSetStabilityWhenIdMutates() {
        // Start as transient/unpersisted (id = null)
        Expense expense = new Expense("Stationery", "Education", new BigDecimal("8.50"), LocalDate.now());
        Set<Expense> set = new HashSet<>();
        set.add(expense);

        assertTrue(set.contains(expense));

        // Simulate repository assigning an ID
        expense.setId(99L);

        // If hashCode depended on mutable `id`, set.contains would now FAIL because
        // it would look in a different bucket. With stable class-based hash, it succeeds!
        assertTrue(set.contains(expense), "Object must still be found in HashSet even after ID assignment");
    }

    @Test
    @DisplayName("toString should contain all field names and values")
    void testToString() {
        Expense expense = new Expense(1L, "Book", "Education", new BigDecimal("20.00"), LocalDate.of(2026, 1, 1));
        String str = expense.toString();

        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("description='Book'"));
        assertTrue(str.contains("category='Education'"));
        assertTrue(str.contains("amount=20.00"));
        assertTrue(str.contains("date=2026-01-01"));
    }
}
