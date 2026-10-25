package com.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;

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
     * Entity identity equality:
     * 
     * <ul>
     *   <li><b>Reflexive:</b> An object is always equal to itself ({@code this == o}).</li>
     *   <li><b>Proxy Compatibility:</b> Uses {@code instanceof Expense} rather than
     *       {@code getClass() == o.getClass()} to support Hibernate / JPA runtime proxies
     *       which dynamically subclass {@code Expense}.</li>
     *   <li><b>Getter Access:</b> Uses {@link #getId()} and {@code expense.getId()} rather than
     *       direct field access so lazy proxies can initialize and return the ID properly.</li>
     *   <li><b>Entity Semantics:</b>
     *       - Persisted expenses (with non-null IDs) are equal if and only if their IDs match.
     *       - Unsaved expenses (with null IDs) only equal themselves (via {@code this == o}).
     *       Two distinct unsaved instances are never equal, strictly preserving transitivity
     *       and preventing erroneous deduplication.</li>
     * </ul>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Expense)) {
            return false;
        }
        Expense expense = (Expense) o;
        return getId() != null && getId().equals(expense.getId());
    }

    /**
     * Stable hash code implementation for entities:
     * 
     * <p>Because an entity's ID can change (from null when unsaved to an assigned value when persisted),
     * including {@code id} in {@code hashCode()} would cause the hash code to change after insertion into
     * a hash-based collection (such as {@link java.util.HashSet} or {@link java.util.HashMap}). This would
     * corrupt collection buckets and make the entity unfindable.
     * 
     * <p>Returning {@code Expense.class.hashCode()} guarantees:
     * <ol>
     *   <li>Stability: The hash code never changes across transient -> persisted state transitions.</li>
     *   <li>Contract fulfillment: If {@code a.equals(b)}, both share the same base class hash code,
     *       ensuring {@code a.hashCode() == b.hashCode()} is always true.</li>
     *   <li>Proxy consistency: Even if {@code this} or {@code o} is a Hibernate proxy subclass,
     *       both reference {@code Expense.class.hashCode()} rather than dynamic proxy classes.</li>
     * </ol>
     */
    @Override
    public int hashCode() {
        return Expense.class.hashCode();
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
