package com.expense.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Utility class providing centralized validation for expense domain data.
 * 
 * <p>Centralizing validation logic adheres to the Single Responsibility Principle (SRP)
 * and ensures identical validation rules are enforced across Console UI, Service layer,
 * and future REST API requests.
 */
public final class InputValidator {

    // Private constructor prevents instantiation of utility class
    private InputValidator() {
    }

    /**
     * Validates an expense description.
     *
     * @param description Narrative description of expense
     * @throws IllegalArgumentException if null or blank
     */
    public static void validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be null or blank.");
        }
    }

    /**
     * Validates an expense category.
     *
     * @param category Category name
     * @throws IllegalArgumentException if null or blank
     */
    public static void validateCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or blank.");
        }
    }

    /**
     * Validates that an amount is non-null and strictly greater than zero.
     *
     * @param amount Monetary amount
     * @throws IllegalArgumentException if null or less than or equal to zero
     */
    public static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be strictly positive (greater than 0).");
        }
    }

    /**
     * Validates that an expense date is provided.
     *
     * @param date Date incurred
     * @throws IllegalArgumentException if null
     */
    public static void validateDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null.");
        }
    }

    /**
     * Validates an expense ID.
     *
     * @param id Identifier to validate
     * @throws IllegalArgumentException if null or less than or equal to zero
     */
    public static void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Expense ID must be a positive number.");
        }
    }

    /**
     * Safely parses a string into a positive BigDecimal.
     *
     * @param amountStr Text representation of amount
     * @return Validated positive BigDecimal
     * @throws IllegalArgumentException if parsing fails or amount is non-positive
     */
    public static BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount cannot be blank.");
        }
        try {
            BigDecimal amount = new BigDecimal(amountStr.trim());
            validateAmount(amount);
            return amount;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format. Please enter a valid number (e.g. 25.50).", e);
        }
    }

    /**
     * Safely parses an ISO-8601 date string (YYYY-MM-DD).
     *
     * @param dateStr Text representation of date
     * @return Validated LocalDate
     * @throws IllegalArgumentException if date format is invalid
     */
    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Date cannot be blank.");
        }
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD (e.g. 2026-10-25).", e);
        }
    }
}
