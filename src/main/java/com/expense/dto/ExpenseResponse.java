package com.expense.dto;

import com.expense.model.Expense;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object representing an expense returned to API clients.
 *
 * <p>Decouples the external REST API response structure from internal entity
 * implementations or database models.
 */
public class ExpenseResponse {

    private Long id;
    private String description;
    private String category;
    private BigDecimal amount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate date;

    public ExpenseResponse() {
    }

    public ExpenseResponse(Long id, String description, String category, BigDecimal amount, LocalDate date) {
        this.id = id;
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.date = date;
    }

    /**
     * Factory method creating an {@link ExpenseResponse} from a domain {@link Expense}.
     *
     * @param expense The source domain expense
     * @return Transformed response DTO, or {@code null} if input is null
     */
    public static ExpenseResponse fromEntity(Expense expense) {
        if (expense == null) {
            return null;
        }
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getDate()
        );
    }

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
}
