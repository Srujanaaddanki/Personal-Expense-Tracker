package com.expense.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object for creating or updating an expense.
 *
 * <p>Encapsulates client input and validates field constraints prior
 * to forwarding values to the domain and service layers.
 */
public class ExpenseRequest {

    @NotBlank(message = "Description cannot be null or blank.")
    private String description;

    @NotBlank(message = "Category cannot be null or blank.")
    private String category;

    @NotNull(message = "Amount cannot be null.")
    @Positive(message = "Amount must be strictly positive (greater than 0).")
    private BigDecimal amount;

    @NotNull(message = "Date cannot be null.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    public ExpenseRequest() {
    }

    public ExpenseRequest(String description, String category, BigDecimal amount, LocalDate date) {
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.date = date;
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
}
