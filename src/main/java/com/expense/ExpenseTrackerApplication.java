package com.expense;

import com.expense.config.RepositoryFactory;
import com.expense.exception.PersistenceException;
import com.expense.repository.ExpenseRepository;
import com.expense.service.ExpenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application entrypoint for the Spring Boot REST API.
 */
@SpringBootApplication
public class ExpenseTrackerApplication {

    private static final Logger log = LoggerFactory.getLogger(ExpenseTrackerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ExpenseTrackerApplication.class, args);
    }

    @Bean
    public ExpenseRepository expenseRepository() {
        return RepositoryFactory.createRepository();
    }

    @Bean
    public ExpenseService expenseService(ExpenseRepository repository) {
        ExpenseService service = new ExpenseService(repository);
        if (service.isPersistenceEnabled()) {
            try {
                service.loadFromStorage();
                log.info("Preloaded {} expense(s) from {}", service.getExpenseCount(), service.getStorageLocation());
            } catch (PersistenceException e) {
                log.warn("Could not preload existing expenses from storage: {}", e.getMessage());
            }
        }
        return service;
    }
}
