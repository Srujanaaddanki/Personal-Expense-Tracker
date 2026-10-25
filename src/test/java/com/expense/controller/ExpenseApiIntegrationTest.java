package com.expense.controller;

import com.expense.dto.ExpenseRequest;
import com.expense.exception.GlobalExceptionHandler;
import com.expense.repository.CsvExpenseRepository;
import com.expense.service.ExpenseService;
import com.expense.storage.CsvExpenseStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Expense REST API End-to-End Integration Tests")
class ExpenseApiIntegrationTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        Path tempCsv = tempDir.resolve("integration-expenses.csv");
        CsvExpenseRepository repository = new CsvExpenseRepository(new CsvExpenseStorage(), tempCsv);
        expenseService = new ExpenseService(repository);
        expenseService.loadFromStorage();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ExpenseController controller = new ExpenseController(expenseService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Should perform full CRUD cycle via HTTP API end-to-end with real file persistence")
    void testFullCrudLifecycle() throws Exception {
        // 1. Initial GET
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        // 2. Create expense 1
        ExpenseRequest req1 = new ExpenseRequest("Lunch with team", "Food", new BigDecimal("32.50"), LocalDate.of(2026, 10, 20));
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/expenses/1")))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.description", is("Lunch with team")))
                .andExpect(jsonPath("$.amount", is(32.50)));

        // 3. Create expense 2
        ExpenseRequest req2 = new ExpenseRequest("Metro card", "Transport", new BigDecimal("15.00"), LocalDate.of(2026, 10, 21));
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/expenses/2")))
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.description", is("Metro card")));

        // 4. Retrieve by ID
        mockMvc.perform(get("/api/expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.category", is("Food")));

        // 5. Update expense 1
        ExpenseRequest updateReq = new ExpenseRequest("Lunch with client", "Food", new BigDecimal("45.00"), LocalDate.of(2026, 10, 20));
        mockMvc.perform(put("/api/expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.description", is("Lunch with client")))
                .andExpect(jsonPath("$.amount", is(45.00)));

        // 6. Delete expense 1
        mockMvc.perform(delete("/api/expenses/1"))
                .andExpect(status().isNoContent());

        // 7. Verify deletion
        mockMvc.perform(get("/api/expenses/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));

        // 8. Retrieve remaining expenses
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(2)));
    }
}
