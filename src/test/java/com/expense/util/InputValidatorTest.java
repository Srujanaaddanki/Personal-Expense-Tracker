package com.expense.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputValidator Unit Tests")
class InputValidatorTest {

    @Test
    @DisplayName("Should accept valid description and reject null/blank")
    void testValidateDescription() {
        assertDoesNotThrow(() -> InputValidator.validateDescription("Textbook"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDescription(null));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDescription(""));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDescription("   "));
    }

    @Test
    @DisplayName("Should accept valid category and reject null/blank")
    void testValidateCategory() {
        assertDoesNotThrow(() -> InputValidator.validateCategory("Food"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateCategory(null));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateCategory(""));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateCategory("   "));
    }

    @Test
    @DisplayName("Should accept strictly positive amount and reject null/zero/negative")
    void testValidateAmount() {
        assertDoesNotThrow(() -> InputValidator.validateAmount(new BigDecimal("0.01")));
        assertDoesNotThrow(() -> InputValidator.validateAmount(new BigDecimal("100.50")));

        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount(null));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateAmount(new BigDecimal("-5.00")));
    }

    @Test
    @DisplayName("Should accept valid date and reject null")
    void testValidateDate() {
        assertDoesNotThrow(() -> InputValidator.validateDate(LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateDate(null));
    }

    @Test
    @DisplayName("Should accept positive ID and reject null/zero/negative")
    void testValidateId() {
        assertDoesNotThrow(() -> InputValidator.validateId(1L));
        assertDoesNotThrow(() -> InputValidator.validateId(500L));

        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateId(null));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateId(0L));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.validateId(-10L));
    }

    @Test
    @DisplayName("Should parse valid amount string and reject malformed/non-positive")
    void testParseAmount() {
        assertEquals(new BigDecimal("45.50"), InputValidator.parseAmount("45.50"));
        assertEquals(new BigDecimal("10"), InputValidator.parseAmount("  10  "));

        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseAmount("not-a-number"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseAmount("-15.00"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseAmount("0"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseAmount(""));
    }

    @Test
    @DisplayName("Should parse valid ISO-8601 date string and reject invalid")
    void testParseDate() {
        assertEquals(LocalDate.of(2026, 10, 25), InputValidator.parseDate("2026-10-25"));

        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseDate("2026-02-30"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseDate("25-10-2026"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseDate("invalid"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.parseDate(""));
    }
}
