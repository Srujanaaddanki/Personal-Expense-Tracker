package com.expense;

import com.expense.service.ExpenseService;
import com.expense.storage.CsvExpenseStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Main Console Application Flow Tests")
class MainTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should simulate adding expenses, searching, sorting, category summary, top-K, and exiting")
    void testMainConsoleFlow() {
        // Simulated inputs:
        // 1 (Add Expense 1) -> "Textbook" -> "Education" -> "50.00" -> "2026-10-15"
        // 1 (Add Expense 2) -> "Coffee" -> "Food" -> "50.00" -> "2026-10-20"
        // 4 (Search by Category) -> "Education"
        // 5 (Filter by Date Range) -> "2026-10-01" -> "2026-10-31"
        // 6 (Sort by Amount) -> 1 (Ascending)
        // 7 (Sort by Date) -> 2 (Descending)
        // 10 (Calculate Total)
        // 11 (Category-wise Spending Summary)
        // 12 (Top K Expenses) -> 2
        // 13 (Exit)
        String simulatedInput = String.join(System.lineSeparator(),
                "1",
                "Textbook",
                "Education",
                "50.00",
                "2026-10-15",
                "1",
                "Coffee",
                "Food",
                "50.00",
                "2026-10-20",
                "4",
                "Education",
                "5",
                "2026-10-01",
                "2026-10-31",
                "6",
                "1",
                "7",
                "2",
                "10",
                "11",
                "12",
                "2",
                "13"
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
        assertTrue(output.contains("[FOUND] 2 expense(s) between 2026-10-01 and 2026-10-31:"));
        assertTrue(output.contains("[SORTED] 2 expense(s) sorted by amount (Lowest to Highest):"));
        assertTrue(output.contains("[SORTED] 2 expense(s) sorted by date (Newest First):"));
        assertTrue(output.contains("Total Amount Spent:      $100.00"));
        assertTrue(output.contains("--- Category-wise Spending Summary ---"));
        assertTrue(output.contains("50.00%"));
        assertTrue(output.contains("--- Top K Highest Expenses ---"));
        assertTrue(output.contains("[TOP 2] Displaying top 2 highest-value expense(s):"));
        assertTrue(output.contains("Thank you for using Personal Expense Tracker. Goodbye!"));
    }

    @Test
    @DisplayName("Should gracefully handle invalid menu choices and non-numeric inputs")
    void testMainHandlesInvalidMenuChoiceGracefully() {
        // Simulated inputs:
        // "abc" (invalid menu number)
        // "99" (out-of-range menu number)
        // "13" (Exit)
        String simulatedInput = String.join(System.lineSeparator(),
                "abc",
                "99",
                "13"
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
        assertTrue(output.contains("Invalid input 'abc'. Please enter a valid number (1-13):"));
        assertTrue(output.contains("Please enter a number between 1 and 13:"));
        assertTrue(output.contains("Thank you for using Personal Expense Tracker. Goodbye!"));
    }

    @Test
    @DisplayName("Main persistence: Automatically loads existing file on startup and auto-saves mutations")
    void testMainConsoleFlowWithPersistence() throws IOException {
        Path testFile = tempDir.resolve("expenses.csv");
        CsvExpenseStorage storage = new CsvExpenseStorage();

        // Run 1: Add an expense and exit
        String run1Input = String.join(System.lineSeparator(),
                "1",
                "Textbook",
                "Education",
                "50.00",
                "2026-10-15",
                "13"
        ) + System.lineSeparator();

        ByteArrayInputStream in1 = new ByteArrayInputStream(run1Input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out1 = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out1));
            ExpenseService service1 = new ExpenseService(storage, testFile);
            Scanner scanner1 = new Scanner(in1);
            Main app1 = new Main(scanner1, service1);
            app1.run();
        } finally {
            System.setOut(originalOut);
        }

        assertTrue(Files.exists(testFile));
        String rawContent = Files.readString(testFile);
        assertTrue(rawContent.contains("Textbook"));
        assertTrue(rawContent.contains("Education"));

        // Run 2: Restart with same file and verify it loads the existing expense
        String run2Input = "13" + System.lineSeparator();
        ByteArrayInputStream in2 = new ByteArrayInputStream(run2Input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out2 = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(out2));
            ExpenseService service2 = new ExpenseService(storage, testFile);
            Scanner scanner2 = new Scanner(in2);
            Main app2 = new Main(scanner2, service2);
            app2.run();
        } finally {
            System.setOut(originalOut);
        }

        String output2 = out2.toString(StandardCharsets.UTF_8);
        assertTrue(output2.contains("[INFO] Loaded 1 existing expense(s)"));
    }

    @Test
    @DisplayName("Main persistence: Gracefully handles malformed CSV on startup without crashing")
    void testMainHandlesMalformedCsvGracefully() throws IOException {
        Path testFile = tempDir.resolve("corrupted.csv");
        Files.writeString(testFile, "id,description,category,amount,date\n1,CorruptRow\n");
        CsvExpenseStorage storage = new CsvExpenseStorage();

        String input = "13" + System.lineSeparator();
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out));
            ExpenseService service = new ExpenseService(storage, testFile);
            Scanner scanner = new Scanner(in);
            Main app = new Main(scanner, service);
            app.run();
        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[ERROR] Failed to load data file"));
        assertTrue(output.contains("[WARNING] To prevent data loss, the corrupted file has NOT been overwritten"));
    }
}
