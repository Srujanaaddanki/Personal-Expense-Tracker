package com.expense.controller;

import com.expense.dto.ExpenseRequest;
import com.expense.dto.ExpenseResponse;
import com.expense.exception.ResourceNotFoundException;
import com.expense.model.Expense;
import com.expense.service.ExpenseService;
import com.expense.util.InputValidator;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing endpoints for expense tracking operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/expenses} - List all expenses with optional filtering/sorting</li>
 *   <li>{@code GET /api/expenses/{id}} - Retrieve a single expense by ID</li>
 *   <li>{@code POST /api/expenses} - Create a new expense</li>
 *   <li>{@code PUT /api/expenses/{id}} - Update an existing expense</li>
 *   <li>{@code DELETE /api/expenses/{id}} - Delete an expense by ID</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /**
     * Lists expenses. Supports optional filtering by category, date range, or top-K,
     * as well as sorting by amount or date.
     *
     * @param category  Optional category filter (case-insensitive)
     * @param startDate Optional starting date boundary (inclusive)
     * @param endDate   Optional ending date boundary (inclusive)
     * @param topK      Optional top-K highest expenses limit
     * @param sortBy    Optional sort field ("amount" or "date")
     * @param ascending Sort direction (defaults to true)
     * @return 200 OK with list of matching expenses as DTOs
     */
    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getAllExpenses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "true") boolean ascending) {

        List<Expense> expenses;

        if (category != null) {
            InputValidator.validateCategory(category);
            expenses = expenseService.getExpensesByCategory(category);
        } else if (startDate != null || endDate != null) {
            expenses = expenseService.getExpensesByDateRange(startDate, endDate);
        } else if (topK != null) {
            expenses = expenseService.getTopKExpenses(topK);
        } else {
            expenses = expenseService.getAllExpenses();
        }

        // Apply secondary date-range filter if combined with category
        if (category != null && (startDate != null || endDate != null)) {
            InputValidator.validateDateRange(startDate, endDate);
            expenses = expenses.stream()
                    .filter(e -> !e.getDate().isBefore(startDate) && !e.getDate().isAfter(endDate))
                    .toList();
        }

        // Apply secondary topK filter if combined with category or date range
        if (topK != null && (category != null || startDate != null || endDate != null)) {
            InputValidator.validateK(topK);
            Comparator<Expense> topComp = Comparator.comparing(Expense::getAmount, Comparator.reverseOrder())
                    .thenComparing(Expense::getId);
            expenses = expenses.stream()
                    .sorted(topComp)
                    .limit(topK)
                    .toList();
        }

        // Apply sorting if requested
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            String normSort = sortBy.trim();
            if ("amount".equalsIgnoreCase(normSort)) {
                Comparator<Expense> comp = ascending
                        ? Comparator.comparing(Expense::getAmount).thenComparing(Expense::getId)
                        : Comparator.comparing(Expense::getAmount, Comparator.reverseOrder()).thenComparing(Expense::getId);
                List<Expense> sorted = new ArrayList<>(expenses);
                sorted.sort(comp);
                expenses = sorted;
            } else if ("date".equalsIgnoreCase(normSort)) {
                Comparator<Expense> comp = ascending
                        ? Comparator.comparing(Expense::getDate).thenComparing(Expense::getId)
                        : Comparator.comparing(Expense::getDate, Comparator.reverseOrder()).thenComparing(Expense::getId);
                List<Expense> sorted = new ArrayList<>(expenses);
                sorted.sort(comp);
                expenses = sorted;
            } else {
                throw new IllegalArgumentException("Unsupported sort field '" + sortBy + "'. Supported fields are 'amount' and 'date'.");
            }
        }

        List<ExpenseResponse> response = expenses.stream()
                .map(ExpenseResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an individual expense by ID.
     *
     * @param id Positive expense ID
     * @return 200 OK with ExpenseResponse, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpenseById(@PathVariable Long id) {
        InputValidator.validateId(id);
        Expense expense = expenseService.getExpenseById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense with ID " + id + " not found."));
        return ResponseEntity.ok(ExpenseResponse.fromEntity(expense));
    }

    /**
     * Creates a new expense.
     *
     * @param request Validated expense request payload
     * @return 201 Created with Location header and ExpenseResponse body
     */
    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request) {
        Expense created = expenseService.addExpense(
                request.getDescription(),
                request.getCategory(),
                request.getAmount(),
                request.getDate()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(ExpenseResponse.fromEntity(created));
    }

    /**
     * Updates an existing expense by ID.
     *
     * @param id      Positive expense ID
     * @param request Validated updated expense details
     * @return 200 OK with updated ExpenseResponse, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request) {
        InputValidator.validateId(id);
        Optional<Expense> updated = expenseService.updateExpense(
                id,
                request.getDescription(),
                request.getCategory(),
                request.getAmount(),
                request.getDate()
        );

        Expense expense = updated.orElseThrow(() ->
                new ResourceNotFoundException("Expense with ID " + id + " not found."));

        return ResponseEntity.ok(ExpenseResponse.fromEntity(expense));
    }

    /**
     * Deletes an expense by ID.
     *
     * @param id Positive expense ID
     * @return 204 No Content, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        InputValidator.validateId(id);
        boolean deleted = expenseService.deleteExpense(id);
        if (!deleted) {
            throw new ResourceNotFoundException("Expense with ID " + id + " not found.");
        }
        return ResponseEntity.noContent().build();
    }
}
