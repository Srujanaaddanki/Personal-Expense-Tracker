package com.expense.controller;

import com.expense.dto.ExpenseRequest;
import com.expense.exception.GlobalExceptionHandler;
import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import com.expense.repository.ExpenseRepository;
import com.expense.service.ExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ExpenseController Unit & MVC Tests")
class ExpenseControllerTest {

    private MockMvc mockMvc;
    private ExpenseService expenseService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService();
        ExpenseController controller = new ExpenseController(expenseService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // --- GET /api/expenses Tests ---

    @Test
    @DisplayName("GET /api/expenses - should return 200 OK and empty list when no expenses exist")
    void testGetAllExpensesEmpty() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/expenses - should return 200 OK with list of expenses")
    void testGetAllExpensesPopulated() throws Exception {
        expenseService.addExpense("Lunch", "Food", new BigDecimal("15.50"), LocalDate.of(2026, 10, 20));
        expenseService.addExpense("Bus fare", "Transport", new BigDecimal("3.25"), LocalDate.of(2026, 10, 21));

        mockMvc.perform(get("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].description", is("Lunch")))
                .andExpect(jsonPath("$[0].category", is("Food")))
                .andExpect(jsonPath("$[0].amount", is(15.50)))
                .andExpect(jsonPath("$[0].date", is("2026-10-20")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].description", is("Bus fare")))
                .andExpect(jsonPath("$[1].category", is("Transport")))
                .andExpect(jsonPath("$[1].amount", is(3.25)))
                .andExpect(jsonPath("$[1].date", is("2026-10-21")));
    }

    @Test
    @DisplayName("GET /api/expenses?category=Food - should filter by category")
    void testGetExpensesByCategory() throws Exception {
        expenseService.addExpense("Dinner", "Food", new BigDecimal("25.00"), LocalDate.of(2026, 10, 22));
        expenseService.addExpense("Ticket", "Transport", new BigDecimal("10.00"), LocalDate.of(2026, 10, 23));

        mockMvc.perform(get("/api/expenses")
                        .param("category", "Food")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description", is("Dinner")))
                .andExpect(jsonPath("$[0].category", is("Food")));
    }

    @Test
    @DisplayName("GET /api/expenses?startDate=...&endDate=... - should filter by date range")
    void testGetExpensesByDateRange() throws Exception {
        expenseService.addExpense("Utilities", "Bills", new BigDecimal("80.00"), LocalDate.of(2026, 10, 15));
        expenseService.addExpense("Holiday", "Travel", new BigDecimal("500.00"), LocalDate.of(2026, 11, 10));

        mockMvc.perform(get("/api/expenses")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description", is("Utilities")))
                .andExpect(jsonPath("$[0].date", is("2026-10-15")));
    }

    @Test
    @DisplayName("GET /api/expenses?topK=2 - should return top K expenses")
    void testGetTopKExpenses() throws Exception {
        expenseService.addExpense("Laptop", "Tech", new BigDecimal("1200.00"), LocalDate.of(2026, 10, 5));
        expenseService.addExpense("Monitor", "Tech", new BigDecimal("350.00"), LocalDate.of(2026, 10, 8));
        expenseService.addExpense("Mouse", "Tech", new BigDecimal("25.00"), LocalDate.of(2026, 10, 9));

        mockMvc.perform(get("/api/expenses")
                        .param("topK", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount", is(1200.00)))
                .andExpect(jsonPath("$[1].amount", is(350.00)));
    }

    @Test
    @DisplayName("GET /api/expenses?sortBy=amount&ascending=true - should sort by amount")
    void testGetExpensesSortedByAmount() throws Exception {
        expenseService.addExpense("Laptop", "Tech", new BigDecimal("1200.00"), LocalDate.of(2026, 10, 5));
        expenseService.addExpense("Coffee", "Food", new BigDecimal("4.50"), LocalDate.of(2026, 10, 1));

        mockMvc.perform(get("/api/expenses")
                        .param("sortBy", "amount")
                        .param("ascending", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].amount", is(4.50)))
                .andExpect(jsonPath("$[1].amount", is(1200.00)));
    }

    @Test
    @DisplayName("GET /api/expenses?sortBy=date&ascending=false - should sort by date descending")
    void testGetExpensesSortedByDate() throws Exception {
        expenseService.addExpense("Flight", "Travel", new BigDecimal("450.00"), LocalDate.of(2026, 10, 24));
        expenseService.addExpense("Coffee", "Food", new BigDecimal("4.50"), LocalDate.of(2026, 10, 1));

        mockMvc.perform(get("/api/expenses")
                        .param("sortBy", "date")
                        .param("ascending", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date", is("2026-10-24")))
                .andExpect(jsonPath("$[1].date", is("2026-10-01")));
    }

    @Test
    @DisplayName("GET /api/expenses?category=   - should return 400 Bad Request when category is blank")
    void testGetExpensesBlankCategory() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("category", "   ")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Category cannot be null or blank")));
    }

    @Test
    @DisplayName("GET /api/expenses?startDate=...&endDate=... - should return 400 Bad Request when date range is reversed")
    void testGetExpensesReversedDateRange() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("startDate", "2026-10-31")
                        .param("endDate", "2026-10-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Start date cannot be after end date")));
    }

    @Test
    @DisplayName("GET /api/expenses?startDate=... - should return 400 Bad Request when endDate is missing")
    void testGetExpensesMissingEndDate() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("startDate", "2026-10-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("End date cannot be null")));
    }

    @Test
    @DisplayName("GET /api/expenses?endDate=... - should return 400 Bad Request when startDate is missing")
    void testGetExpensesMissingStartDate() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("endDate", "2026-10-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Start date cannot be null")));
    }

    @Test
    @DisplayName("GET /api/expenses?topK=0 - should return 400 Bad Request when topK is non-positive")
    void testGetExpensesInvalidTopK() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("topK", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("K must be a positive integer")));
    }

    @Test
    @DisplayName("GET /api/expenses?sortBy=unsupported - should return 400 Bad Request when sortBy field is invalid")
    void testGetExpensesUnsupportedSortBy() throws Exception {
        mockMvc.perform(get("/api/expenses")
                        .param("sortBy", "invalidField")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Unsupported sort field 'invalidField'")));
    }

    @Test
    @DisplayName("GET /api/expenses - should handle combination of category filter and amount sorting")
    void testGetExpensesCombinedFilterAndSort() throws Exception {
        expenseService.addExpense("Dinner", "Food", new BigDecimal("45.00"), LocalDate.of(2026, 10, 22));
        expenseService.addExpense("Snack", "Food", new BigDecimal("5.00"), LocalDate.of(2026, 10, 23));
        expenseService.addExpense("Bus", "Transport", new BigDecimal("10.00"), LocalDate.of(2026, 10, 23));

        mockMvc.perform(get("/api/expenses")
                        .param("category", "Food")
                        .param("sortBy", "amount")
                        .param("ascending", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description", is("Dinner")))
                .andExpect(jsonPath("$[0].amount", is(45.00)))
                .andExpect(jsonPath("$[1].description", is("Snack")))
                .andExpect(jsonPath("$[1].amount", is(5.00)));
    }

    // --- GET /api/expenses/{id} Tests ---

    @Test
    @DisplayName("GET /api/expenses/{id} - should return 200 OK when expense exists")
    void testGetExpenseByIdFound() throws Exception {
        Expense added = expenseService.addExpense("Textbook", "Education", new BigDecimal("75.00"), LocalDate.of(2026, 10, 10));

        mockMvc.perform(get("/api/expenses/" + added.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(added.getId().intValue())))
                .andExpect(jsonPath("$.description", is("Textbook")))
                .andExpect(jsonPath("$.category", is("Education")))
                .andExpect(jsonPath("$.amount", is(75.00)))
                .andExpect(jsonPath("$.date", is("2026-10-10")));
    }

    @Test
    @DisplayName("GET /api/expenses/{id} - should return 404 Not Found when ID does not exist")
    void testGetExpenseByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/expenses/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Expense with ID 999 not found")))
                .andExpect(jsonPath("$.path", is("/api/expenses/999")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/expenses/{id} - should return 400 Bad Request when ID is non-positive")
    void testGetExpenseByIdNonPositive() throws Exception {
        mockMvc.perform(get("/api/expenses/0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Expense ID must be a positive number")));
    }

    @Test
    @DisplayName("GET /api/expenses/{id} - should return 400 Bad Request when ID is not a number")
    void testGetExpenseByIdNonNumeric() throws Exception {
        mockMvc.perform(get("/api/expenses/invalid-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Parameter 'id' should be of type")));
    }

    // --- POST /api/expenses Tests ---

    @Test
    @DisplayName("POST /api/expenses - should return 201 Created with Location header and response body")
    void testCreateExpenseSuccess() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Groceries", "Food", new BigDecimal("54.20"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/expenses/1")))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.description", is("Groceries")))
                .andExpect(jsonPath("$.category", is("Food")))
                .andExpect(jsonPath("$.amount", is(54.20)))
                .andExpect(jsonPath("$.date", is("2026-10-25")));

        assertEquals(1, expenseService.getAllExpenses().size());
    }

    @Test
    @DisplayName("POST /api/expenses - should return 400 Bad Request when description is blank")
    void testCreateExpenseBlankDescription() throws Exception {
        ExpenseRequest request = new ExpenseRequest("   ", "Food", new BigDecimal("10.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.details", notNullValue()));

        assertEquals(0, expenseService.getAllExpenses().size());
    }

    @Test
    @DisplayName("POST /api/expenses - should return 400 Bad Request when category is blank")
    void testCreateExpenseBlankCategory() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Lunch", "", new BigDecimal("10.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.details", notNullValue()));

        assertEquals(0, expenseService.getAllExpenses().size());
    }

    @Test
    @DisplayName("POST /api/expenses - should return 400 Bad Request when amount is negative or zero")
    void testCreateExpenseNonPositiveAmount() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Lunch", "Food", new BigDecimal("0.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.details", notNullValue()));

        assertEquals(0, expenseService.getAllExpenses().size());
    }

    @Test
    @DisplayName("POST /api/expenses - should return 400 Bad Request when date is null")
    void testCreateExpenseNullDate() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Lunch", "Food", new BigDecimal("12.00"), null);

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        assertEquals(0, expenseService.getAllExpenses().size());
    }

    @Test
    @DisplayName("POST /api/expenses - should return 400 Bad Request when JSON is unparseable")
    void testCreateExpenseMalformedJson() throws Exception {
        String malformedJson = "{ \"description\": \"Coffee\", \"amount\": \"not-a-number\" ";

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Malformed or unreadable JSON")));

        assertEquals(0, expenseService.getAllExpenses().size());
    }

    // --- PUT /api/expenses/{id} Tests ---

    @Test
    @DisplayName("PUT /api/expenses/{id} - should return 200 OK when update succeeds")
    void testUpdateExpenseSuccess() throws Exception {
        Expense added = expenseService.addExpense("Lunch", "Food", new BigDecimal("12.00"), LocalDate.of(2026, 10, 25));
        ExpenseRequest request = new ExpenseRequest("Updated Lunch", "Food", new BigDecimal("18.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(put("/api/expenses/" + added.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(added.getId().intValue())))
                .andExpect(jsonPath("$.description", is("Updated Lunch")))
                .andExpect(jsonPath("$.amount", is(18.00)));

        Optional<Expense> found = expenseService.getExpenseById(added.getId());
        assertTrue(found.isPresent());
        assertEquals("Updated Lunch", found.get().getDescription());
        assertEquals(new BigDecimal("18.00"), found.get().getAmount());
    }

    @Test
    @DisplayName("PUT /api/expenses/{id} - should return 404 Not Found when updating non-existent expense")
    void testUpdateExpenseNotFound() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Snack", "Food", new BigDecimal("5.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(put("/api/expenses/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Expense with ID 999 not found")));
    }

    @Test
    @DisplayName("PUT /api/expenses/{id} - should return 400 Bad Request when path ID is non-positive")
    void testUpdateExpenseNonPositiveId() throws Exception {
        ExpenseRequest request = new ExpenseRequest("Snack", "Food", new BigDecimal("5.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(put("/api/expenses/-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Expense ID must be a positive number")));
    }

    // --- DELETE /api/expenses/{id} Tests ---

    @Test
    @DisplayName("DELETE /api/expenses/{id} - should return 204 No Content when delete succeeds")
    void testDeleteExpenseSuccess() throws Exception {
        Expense added = expenseService.addExpense("Coffee", "Food", new BigDecimal("4.00"), LocalDate.of(2026, 10, 25));

        mockMvc.perform(delete("/api/expenses/" + added.getId()))
                .andExpect(status().isNoContent());

        assertTrue(expenseService.getExpenseById(added.getId()).isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/expenses/{id} - should return 404 Not Found when ID does not exist")
    void testDeleteExpenseNotFound() throws Exception {
        mockMvc.perform(delete("/api/expenses/888"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Expense with ID 888 not found")));
    }

    @Test
    @DisplayName("DELETE /api/expenses/{id} - should return 400 Bad Request when ID is non-positive")
    void testDeleteExpenseNonPositiveId() throws Exception {
        mockMvc.perform(delete("/api/expenses/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Expense ID must be a positive number")));
    }

    // --- Exception & Persistence Tests ---

    @Test
    @DisplayName("POST /api/expenses - should return 500 Internal Server Error when PersistenceException is thrown")
    void testCreateExpensePersistenceException() throws Exception {
        ExpenseService failingService = new ExpenseService(new FailingRepository());
        ExpenseController failingController = new ExpenseController(failingService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        MockMvc failingMockMvc = MockMvcBuilders.standaloneSetup(failingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        ExpenseRequest request = new ExpenseRequest("Groceries", "Food", new BigDecimal("50.00"), LocalDate.of(2026, 10, 25));

        failingMockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", containsString("A storage persistence error occurred")));
    }

    static class FailingRepository implements ExpenseRepository {
        @Override
        public Optional<Expense> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<Expense> findAll() {
            return Collections.emptyList();
        }

        @Override
        public Expense save(Expense expense) {
            throw new PersistenceException("Disk I/O error occurred while writing CSV");
        }

        @Override
        public Optional<Expense> update(Expense expense) {
            throw new PersistenceException("Disk I/O error occurred while updating CSV");
        }

        @Override
        public boolean deleteById(Long id) {
            throw new PersistenceException("Disk I/O error occurred while deleting from CSV");
        }

        @Override
        public String getStorageLocation() {
            return "failing://storage";
        }
    }
}
