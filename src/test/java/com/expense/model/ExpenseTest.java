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

    /**
     * Test subclass to simulate a runtime proxy (such as a Hibernate ByteBuddy proxy).
     * Proxies subclass the entity and intercept getter calls.
     */
    static class ExpenseProxyStub extends Expense {
        private final Long proxyId;

        public ExpenseProxyStub(Long proxyId) {
            this.proxyId = proxyId;
        }

        @Override
        public Long getId() {
            return this.proxyId;
        }
    }

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

    // --- Entity Identity & Equality Contract Tests ---

    @Test
    @DisplayName("Reflexivity: An object must equal itself whether saved or unsaved")
    void testEqualsReflexivity() {
        Expense unsaved = new Expense("Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());
        Expense saved = new Expense(1L, "Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());

        assertEquals(unsaved, unsaved, "Unsaved entity must equal itself");
        assertEquals(saved, saved, "Saved entity must equal itself");
    }

    @Test
    @DisplayName("Symmetry: a.equals(b) must equal b.equals(a)")
    void testEqualsSymmetry() {
        Expense a1 = new Expense(1L, "Lunch", "Food", new BigDecimal("10.00"), LocalDate.now());
        Expense a2 = new Expense(1L, "Different Lunch", "Food", new BigDecimal("12.00"), LocalDate.now());
        Expense b = new Expense(2L, "Dinner", "Food", new BigDecimal("20.00"), LocalDate.now());
        Expense unsaved1 = new Expense("Snack", "Food", new BigDecimal("3.00"), LocalDate.now());
        Expense unsaved2 = new Expense("Snack", "Food", new BigDecimal("3.00"), LocalDate.now());

        // Same ID: symmetric true
        assertTrue(a1.equals(a2) && a2.equals(a1));

        // Different ID: symmetric false
        assertFalse(a1.equals(b));
        assertFalse(b.equals(a1));

        // Saved vs Unsaved: symmetric false
        assertFalse(a1.equals(unsaved1));
        assertFalse(unsaved1.equals(a1));

        // Distinct Unsaved: symmetric false
        assertFalse(unsaved1.equals(unsaved2));
        assertFalse(unsaved2.equals(unsaved1));
    }

    @Test
    @DisplayName("Transitivity: If a.equals(b) and b.equals(c), then a.equals(c)")
    void testEqualsTransitivity() {
        Expense a = new Expense(1L, "Book A", "Education", new BigDecimal("25.00"), LocalDate.now());
        Expense b = new Expense(1L, "Book B", "Education", new BigDecimal("25.00"), LocalDate.now());
        Expense c = new Expense(1L, "Book C", "Education", new BigDecimal("30.00"), LocalDate.now());

        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c, "Transitivity must hold for entities with the same ID");

        // Verify the previous hazard scenario is eliminated:
        Expense saved1 = new Expense(1L, "Coffee", "Food", new BigDecimal("5.00"), LocalDate.now());
        Expense unsaved = new Expense("Coffee", "Food", new BigDecimal("5.00"), LocalDate.now());
        Expense saved2 = new Expense(2L, "Coffee", "Food", new BigDecimal("5.00"), LocalDate.now());

        assertNotEquals(saved1, unsaved, "Saved entity must not equal unsaved entity");
        assertNotEquals(unsaved, saved2, "Unsaved entity must not equal saved entity");
        assertNotEquals(saved1, saved2, "Entities with distinct IDs must not be equal");
    }

    @Test
    @DisplayName("Null safety and type safety: equals(null) and equals(different type) must be false")
    void testEqualsNullAndTypeSafety() {
        Expense expense = new Expense(1L, "Coffee", "Food", new BigDecimal("4.50"), LocalDate.now());

        assertFalse(expense.equals(null), "equals(null) must return false");
        assertFalse(expense.equals("Not an Expense"), "equals(incompatibleType) must return false");
    }

    @Test
    @DisplayName("Distinct unsaved expenses with null IDs must NOT be equal")
    void testDistinctUnsavedExpensesNotEqual() {
        LocalDate today = LocalDate.now();
        Expense expense1 = new Expense("Subway", "Transport", new BigDecimal("2.50"), today);
        Expense expense2 = new Expense("Subway", "Transport", new BigDecimal("2.50"), today);

        assertNotEquals(expense1, expense2, "Distinct unpersisted expenses must not be equal, avoiding erroneous deduplication");
    }

    @Test
    @DisplayName("Persisted expenses with matching non-null IDs are equal (Entity Identity)")
    void testMatchingPersistedIdsEqual() {
        Expense expense1 = new Expense(100L, "Old Title", "Category A", new BigDecimal("10.00"), LocalDate.now());
        Expense expense2 = new Expense(100L, "New Title", "Category B", new BigDecimal("99.00"), LocalDate.now());

        assertEquals(expense1, expense2, "Persisted entities with matching IDs represent the same record");
    }

    @Test
    @DisplayName("Persisted expenses with different IDs are NOT equal")
    void testDifferentPersistedIdsNotEqual() {
        Expense expense1 = new Expense(1L, "Groceries", "Food", new BigDecimal("50.00"), LocalDate.now());
        Expense expense2 = new Expense(2L, "Groceries", "Food", new BigDecimal("50.00"), LocalDate.now());

        assertNotEquals(expense1, expense2);
    }

    @Test
    @DisplayName("One persisted expense and one unsaved expense are NOT equal")
    void testPersistedAndUnsavedNotEqual() {
        Expense persisted = new Expense(1L, "Coffee", "Food", new BigDecimal("4.00"), LocalDate.now());
        Expense unsaved = new Expense("Coffee", "Food", new BigDecimal("4.00"), LocalDate.now());

        assertNotEquals(persisted, unsaved);
        assertNotEquals(unsaved, persisted);
    }

    // --- HashCode & Collection Stability Tests ---

    @Test
    @DisplayName("HashCode contract: Equal objects must have identical hashCodes")
    void testHashCodeContract() {
        Expense expense1 = new Expense(5L, "Dinner", "Food", new BigDecimal("30.00"), LocalDate.now());
        Expense expense2 = new Expense(5L, "Dinner", "Food", new BigDecimal("30.00"), LocalDate.now());

        assertEquals(expense1, expense2);
        assertEquals(expense1.hashCode(), expense2.hashCode(), "Equal entities must produce identical hashCodes");
    }

    @Test
    @DisplayName("HashSet stability: Object remains retrievable in HashSet when ID changes from null to persisted")
    void testHashSetStabilityWhenIdMutates() {
        // Start as transient/unsaved (id = null)
        Expense expense = new Expense("Stationery", "Education", new BigDecimal("8.50"), LocalDate.now());
        Set<Expense> set = new HashSet<>();
        set.add(expense);

        assertTrue(set.contains(expense), "Entity must be present initially");

        // Simulate repository/database assigning an ID upon saving
        expense.setId(99L);

        // Constant class-level hash code ensures bucket location never moves
        assertTrue(set.contains(expense), "Entity must still be found in HashSet even after ID assignment");
    }

    // --- Hibernate / JPA Proxy Compatibility Test ---

    @Test
    @DisplayName("Proxy compatibility: Expense equals a proxy subclass with the same ID symmetrically")
    void testHibernateProxyCompatibility() {
        Long sharedId = 42L;
        Expense realEntity = new Expense(sharedId, "Cloud Hosting", "Services", new BigDecimal("15.00"), LocalDate.now());
        Expense proxyStub = new ExpenseProxyStub(sharedId);

        // Symmetry with proxy subclass
        assertEquals(realEntity, proxyStub, "Real entity must equal proxy subclass with same ID");
        assertEquals(proxyStub, realEntity, "Proxy subclass must equal real entity with same ID");

        // Hash code contract with proxy subclass
        assertEquals(realEntity.hashCode(), proxyStub.hashCode(), "Proxy and real entity must have matching hashCodes");
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
