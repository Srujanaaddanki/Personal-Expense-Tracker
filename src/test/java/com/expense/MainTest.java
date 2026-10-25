package com.expense;

import com.expense.service.ExpenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Main Console Application Flow Tests")
class MainTest {

    @Test
    @DisplayName("Should simulate adding an expense, searching by category, filtering by date, and exiting")
    void testMainConsoleFlow() {
        // Simulated inputs:
        // 1 (Add Expense) -> "Textbook" -> "Education" -> "50.00" -> "2026-10-25"
        // 4 (Search by Category) -> "Education"
        // 5 (Filter by Date Range) -> "2026-10-01" -> "2026-10-31"
        // 8 (Calculate Total)
        // 9 (Exit)
        String simulatedInput = String.join(System.lineSeparator(),
                "1",
                "Textbook",
                "Education",
                "50.00",
                "2026-10-25",
                "4",
                "Education",
                "5",
                "2026-10-01",
                "2026-10-31",
                "8",
                "9"
        ) + System.lineSeparator();

        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out));
            ExpenseService service = new ExpenseService();
            Scanner scanner = new Scanner(in);
            Main app = new Main(scanner, service);
            app.run();
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Expense recorded successfully!"));
        assertTrue(output.contains("[FOUND] 1 expense(s) in category 'Education':"));
        assertTrue(output.contains("[FOUND] 1 expense(s) between 2026-10-01 and 2026-10-31:"));
        assertTrue(output.contains("Total Amount Spent:      $50.00"));
        assertTrue(output.contains("Thank you for using Personal Expense Tracker. Goodbye!"));
    }

    @Test
    @DisplayName("Should gracefully handle invalid menu choices and non-numeric inputs")
    void testMainHandlesInvalidMenuChoiceGracefully() {
        // Simulated inputs:
        // "abc" (invalid menu number)
        // "99" (out-of-range menu number)
        // "9" (Exit)
        String simulatedInput = String.join(System.lineSeparator(),
                "abc",
                "99",
                "9"
        ) + System.lineSeparator();

        ByteArrayInputStream in = new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out));
            ExpenseService service = new ExpenseService();
            Scanner scanner = new Scanner(in);
            Main app = new Main(scanner, service);
            app.run();
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Invalid input 'abc'. Please enter a valid number (1-9):"));
        assertTrue(output.contains("Please enter a number between 1 and 9:"));
        assertTrue(output.contains("Thank you for using Personal Expense Tracker. Goodbye!"));
    }
}
