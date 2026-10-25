package com.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents an individual expense in the Personal Expense Tracker.
 * 
 * <p>Key Design Choices:
 * <ul>
 *   <li><b>BigDecimal for amount:</b> Avoids floating-point precision issues inherent
 *       in {@code float} and {@code double} (e.g. 0.1 + 0.2 != 0.3 in binary floating-point).</li>
 *   <li><b>LocalDate for date:</b> Immutable, thread-safe, and ISO-8601 compliant modern
 *       Java Time API, replacing legacy mutable {@code java.util.Date}.</li>
 * </ul>
 */
public class Expense {

    private Long id;
    private String description;
    private String category;
    private BigDecimal amount;
    private LocalDate date;

    /**
     * Default no-argument constructor.
     * Required for serialization/deserialization frameworks and JPA reflection.
     */
    public Expense() {
    }

    /**
     * Parameterized constructor without ID.
     * Useful when creating new expenses before an ID is assigned by the repository or database.
     *
     * @param description Short narrative of the expense (e.g. "Grocery shopping")
     * @param category    Expense category (e.g. "Food", "Transport", "Utilities")
     * @param amount      Monetary value represented as positive {@link BigDecimal}
     * @param date        Date the expense was incurred
     */
    public Expense(String description, String category, BigDecimal amount, LocalDate date) {
        this(null, description, category, amount, date);
    }

    /**
     * Full parameterized constructor.
     *
     * @param id          Unique expense identifier
     * @param description Short narrative of the expense
     * @param category    Expense category
     * @param amount      Monetary value
     * @param date        Date incurred
     */
    public Expense(Long id, String description, String category, BigDecimal amount, LocalDate date) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.date = date;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    // --- Identity, Equality, and Hashing ---

    /**
     * Equals implementation considering the trade-offs of identity vs state:
     * 
     * 1. Two references pointing to the same memory address are always equal (reflexive).
     * 2. Non-Expense objects or null references are never equal.
     * 3. If both instances possess a non-null ID, equality is determined strictly by the ID,
     *    which represents database/storage primary key identity.
     * 4. If an instance lacks an ID (transient/unpersisted), equality falls back to
     *    matching all core business attributes (description, category, amount, date).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Expense expense = (Expense) o;

        // If both entities have an assigned ID, compare by ID
        if (id != null && expense.id != null) {
            return Objects.equals(id, expense.id);
        }

        // Otherwise (for unpersisted entities), fall back to business attributes
        return Objects.equals(description, expense.description) &&
               Objects.equals(category, expense.category) &&
               Objects.equals(amount, expense.amount) &&
               Objects.equals(date, expense.date);
    }

    /**
     * HashCode implementation considering mutable ID trade-offs:
     * 
     * If an object's ID changes (e.g. from null to an assigned ID after saving),
     * relying on `id` in hashCode() would mutate the hash code after insertion into
     * a HashSet or HashMap. This causes the object to become "lost" in the wrong bucket.
     * 
     * For entities, returning a constant or class-based hash ensures the hash code
     * remains stable across all state transitions (transient -> persisted -> detached),
     * fulfilling the contract: if a.equals(b), then a.hashCode() == b.hashCode().
     */
    @Override
    public int hashCode() {
        // Stable class-based hash code prevents bucket corruption in hash collections
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Expense{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", amount=" + amount +
                ", date=" + date +
                '}';
    }
}
