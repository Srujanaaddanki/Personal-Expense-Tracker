package com.expense;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import com.expense.service.ExpenseService;
import com.expense.storage.CsvExpenseStorage;
import com.expense.util.InputValidator;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console-based user interface for the Personal Expense Tracker.
 * 
 * <p>Key Engineering Design:
 * <ul>
 *   <li><b>Separation of UI and Business Logic:</b> The UI handles presentation, menu rendering,
 *       and user interaction. It delegates all operations to {@link ExpenseService}.</li>
 *   <li><b>Resilient Input Handling:</b> All numeric and date inputs are validated and wrapped in
 *       guarded loops. Non-numeric or malformed entries are rejected gracefully without crashing.</li>
 * </ul>
 */
public class Main {

    private final ExpenseService expenseService;
    private final Scanner scanner;

    public static final Path DEFAULT_DATA_PATH = Path.of("data", "expenses.csv");

    public Main(Scanner scanner, ExpenseService expenseService) {
        this.scanner = scanner;
        this.expenseService = expenseService;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        CsvExpenseStorage storage = new CsvExpenseStorage();
        ExpenseService service = new ExpenseService(storage, DEFAULT_DATA_PATH);
        Main app = new Main(scanner, service);
        app.run();
    }

    /**
     * Main application loop displaying the interactive menu.
     */
    public void run() {
        System.out.println("==================================================");
        System.out.println("       PERSONAL EXPENSE TRACKER (STAGE A)");
        System.out.println("==================================================");

        if (expenseService.isPersistenceEnabled()) {
            try {
                expenseService.loadFromStorage();
                int loadedCount = expenseService.getExpenseCount();
                if (loadedCount > 0) {
                    System.out.printf("[INFO] Loaded %d existing expense(s) from '%s'.%n",
                            loadedCount, expenseService.getDataFilePath());
                } else {
                    System.out.printf("[INFO] No existing expense data found at '%s'. A new file will be created on first save.%n",
                            expenseService.getDataFilePath());
                }
            } catch (PersistenceException e) {
                System.out.println("\n[ERROR] Failed to load data file: " + e.getMessage());
                System.out.println("[WARNING] To prevent data loss, the corrupted file has NOT been overwritten.");
                System.out.println("[WARNING] Starting with an empty tracker in memory.\n");
            }
        }

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readMenuChoice(1, 13);

            switch (choice) {
                case 1 -> handleAddExpense();
                case 2 -> handleViewAllExpenses();
                case 3 -> handleSearchExpenseById();
                case 4 -> handleSearchExpensesByCategory();
                case 5 -> handleFilterExpensesByDateRange();
                case 6 -> handleSortExpensesByAmount();
                case 7 -> handleSortExpensesByDate();
                case 8 -> handleUpdateExpense();
                case 9 -> handleDeleteExpense();
                case 10 -> handleCalculateTotalSpending();
                case 11 -> handleCategorySpendingSummary();
                case 12 -> handleTopKExpenses();
                case 13 -> {
                    System.out.println("\nThank you for using Personal Expense Tracker. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void printMenu() {
        System.out.println("\n----------------- MAIN MENU -----------------");
        System.out.println("1. Add an Expense");
        System.out.println("2. View All Expenses");
        System.out.println("3. Search Expense by ID");
        System.out.println("4. Search Expenses by Category");
        System.out.println("5. Filter Expenses by Date Range");
        System.out.println("6. Sort Expenses by Amount");
        System.out.println("7. Sort Expenses by Date");
        System.out.println("8. Update an Expense");
        System.out.println("9. Delete an Expense");
        System.out.println("10. Calculate Total Spending");
        System.out.println("11. Category-wise Spending Summary");
        System.out.println("12. Top K Highest Expenses");
        System.out.println("13. Exit");
        System.out.print("Please enter your choice (1-13): ");
    }

    private void handleAddExpense() {
        System.out.println("\n--- Add New Expense ---");
        String description = readNonBlankString("Enter Description (e.g. Textbooks): ");
        String category = readNonBlankString("Enter Category (e.g. Education): ");
        BigDecimal amount = readPositiveAmount("Enter Amount (e.g. 45.50): ");
        LocalDate date = readValidDate("Enter Date (YYYY-MM-DD or press Enter for today): ", true);

        try {
            Expense created = expenseService.addExpense(description, category, amount, date);
            System.out.println("\n[SUCCESS] Expense recorded successfully!");
            printExpenseRow(created);
        } catch (IllegalArgumentException | IllegalStateException | PersistenceException e) {
            System.out.println("\n[ERROR] Failed to add expense: " + e.getMessage());
        }
    }

    private void handleViewAllExpenses() {
        System.out.println("\n--- All Recorded Expenses ---");
        List<Expense> expenses = expenseService.getAllExpenses();
        if (expenses.isEmpty()) {
            System.out.println("No expenses recorded yet. Use Option 1 to add your first expense!");
            return;
        }

        printExpenseTable(expenses);
    }

    private void handleSearchExpenseById() {
        System.out.println("\n--- Search Expense by ID ---");
        long id = readPositiveLong("Enter Expense ID: ");
        Optional<Expense> found = expenseService.getExpenseById(id);

        if (found.isPresent()) {
            System.out.println("\n[FOUND] Expense details:");
            printExpenseTable(List.of(found.get()));
        } else {
            System.out.println("\n[NOT FOUND] No expense exists with ID: " + id);
        }
    }

    private void handleSearchExpensesByCategory() {
        System.out.println("\n--- Search Expenses by Category ---");
        String category = readNonBlankString("Enter Category to search (e.g. Food): ");
        List<Expense> matches = expenseService.getExpensesByCategory(category);

        if (matches.isEmpty()) {
            System.out.printf("\n[NOT FOUND] No expenses found in category: '%s'%n", category);
        } else {
            System.out.printf("\n[FOUND] %d expense(s) in category '%s':%n", matches.size(), category);
            printExpenseTable(matches);
        }
    }

    private void handleFilterExpensesByDateRange() {
        System.out.println("\n--- Filter Expenses by Date Range ---");
        LocalDate startDate = readValidDate("Enter Start Date (YYYY-MM-DD): ", false);
        LocalDate endDate = readValidDate("Enter End Date (YYYY-MM-DD): ", false);

        try {
            List<Expense> matches = expenseService.getExpensesByDateRange(startDate, endDate);
            if (matches.isEmpty()) {
                System.out.printf("\n[NOT FOUND] No expenses found between %s and %s.%n", startDate, endDate);
            } else {
                System.out.printf("\n[FOUND] %d expense(s) between %s and %s:%n", matches.size(), startDate, endDate);
                printExpenseTable(matches);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    private void handleSortExpensesByAmount() {
        System.out.println("\n--- Sort Expenses by Amount ---");
        List<Expense> all = expenseService.getAllExpenses();
        if (all.isEmpty()) {
            System.out.println("No expenses recorded yet.");
            return;
        }

        System.out.println("Select Direction:");
        System.out.println("1. Ascending (Lowest to Highest)");
        System.out.println("2. Descending (Highest to Lowest)");
        System.out.print("Enter choice (1-2): ");
        int direction = readMenuChoice(1, 2);

        boolean ascending = (direction == 1);
        List<Expense> sorted = expenseService.getExpensesSortedByAmount(ascending);
        System.out.printf("\n[SORTED] %d expense(s) sorted by amount (%s):%n",
                sorted.size(), ascending ? "Lowest to Highest" : "Highest to Lowest");
        printExpenseTable(sorted);
    }

    private void handleSortExpensesByDate() {
        System.out.println("\n--- Sort Expenses by Date ---");
        List<Expense> all = expenseService.getAllExpenses();
        if (all.isEmpty()) {
            System.out.println("No expenses recorded yet.");
            return;
        }

        System.out.println("Select Direction:");
        System.out.println("1. Ascending (Oldest First)");
        System.out.println("2. Descending (Newest First)");
        System.out.print("Enter choice (1-2): ");
        int direction = readMenuChoice(1, 2);

        boolean ascending = (direction == 1);
        List<Expense> sorted = expenseService.getExpensesSortedByDate(ascending);
        System.out.printf("\n[SORTED] %d expense(s) sorted by date (%s):%n",
                sorted.size(), ascending ? "Oldest First" : "Newest First");
        printExpenseTable(sorted);
    }

    private void handleUpdateExpense() {
        System.out.println("\n--- Update an Expense ---");
        long id = readPositiveLong("Enter the ID of the expense you want to update: ");
        Optional<Expense> existingOpt = expenseService.getExpenseById(id);

        if (existingOpt.isEmpty()) {
            System.out.println("\n[NOT FOUND] No expense exists with ID: " + id);
            return;
        }

        Expense current = existingOpt.get();
        System.out.println("Current details: " + current);

        String newDescription = readNonBlankString("Enter New Description: ");
        String newCategory = readNonBlankString("Enter New Category: ");
        BigDecimal newAmount = readPositiveAmount("Enter New Amount: ");
        LocalDate newDate = readValidDate("Enter New Date (YYYY-MM-DD): ", false);

        try {
            Optional<Expense> updated = expenseService.updateExpense(id, newDescription, newCategory, newAmount, newDate);
            if (updated.isPresent()) {
                System.out.println("\n[SUCCESS] Expense updated successfully!");
                printExpenseRow(updated.get());
            }
        } catch (IllegalArgumentException | IllegalStateException | PersistenceException e) {
            System.out.println("\n[ERROR] Failed to update expense: " + e.getMessage());
        }
    }

    private void handleDeleteExpense() {
        System.out.println("\n--- Delete an Expense ---");
        long id = readPositiveLong("Enter Expense ID to delete: ");

        try {
            boolean deleted = expenseService.deleteExpense(id);
            if (deleted) {
                System.out.println("\n[SUCCESS] Expense with ID " + id + " was deleted.");
            } else {
                System.out.println("\n[NOT FOUND] No expense exists with ID: " + id);
            }
        } catch (IllegalArgumentException | PersistenceException e) {
            System.out.println("\n[ERROR] Failed to delete expense: " + e.getMessage());
        }
    }

    private void handleCalculateTotalSpending() {
        System.out.println("\n--- Total Spending Summary ---");
        BigDecimal total = expenseService.calculateTotalSpending();
        int count = expenseService.getExpenseCount();

        System.out.printf("Total Recorded Expenses: %d%n", count);
        System.out.printf("Total Amount Spent:      $%.2f%n", total);
    }

    private void handleCategorySpendingSummary() {
        System.out.println("\n--- Category-wise Spending Summary ---");
        List<Expense> all = expenseService.getAllExpenses();
        if (all.isEmpty()) {
            System.out.println("No expenses recorded yet.");
            return;
        }

        BigDecimal grandTotal = expenseService.calculateTotalSpending();
        Map<String, BigDecimal> categoryTotals = expenseService.calculateCategorySpending();
        Map<String, BigDecimal> categoryPercentages = expenseService.calculateCategoryPercentages(categoryTotals, grandTotal);

        List<Map.Entry<String, BigDecimal>> sortedEntries = new ArrayList<>(categoryTotals.entrySet());
        sortedEntries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));

        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-20s | %-16s | %-15s%n", "Category", "Total Spent", "Share of Total");
        System.out.println("------------------------------------------------------------------");
        for (Map.Entry<String, BigDecimal> entry : sortedEntries) {
            String category = entry.getKey();
            BigDecimal total = entry.getValue();
            BigDecimal pct = categoryPercentages.getOrDefault(category, BigDecimal.ZERO);
            System.out.printf("%-20s | $%-15.2f | %-14s%n",
                    truncate(category, 20),
                    total,
                    pct.toPlainString() + "%");
        }
        System.out.println("------------------------------------------------------------------");
        System.out.printf("%-20s | $%-15.2f | %-14s%n", "OVERALL TOTAL", grandTotal, "-");
        System.out.println("------------------------------------------------------------------");
    }

    private void handleTopKExpenses() {
        System.out.println("\n--- Top K Highest Expenses ---");
        List<Expense> all = expenseService.getAllExpenses();
        if (all.isEmpty()) {
            System.out.println("No expenses recorded yet.");
            return;
        }

        int k = readPositiveInt("Enter number of top expenses to display (K): ");
        try {
            List<Expense> topK = expenseService.getTopKExpenses(k);
            System.out.printf("\n[TOP %d] Displaying top %d highest-value expense(s):%n", topK.size(), topK.size());
            printExpenseTable(topK);
        } catch (IllegalArgumentException e) {
            System.out.println("\n[ERROR] " + e.getMessage());
        }
    }

    // --- Safe, Non-Crashing Input Helper Methods ---

    private int readMenuChoice(int min, int max) {
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(line);
                if (choice >= min && choice <= max) {
                    return choice;
                }
                System.out.printf("Please enter a number between %d and %d: ", min, max);
            } catch (NumberFormatException e) {
                System.out.printf("Invalid input '%s'. Please enter a valid number (%d-%d): ", line, min, max);
            }
        }
    }

    private int readPositiveInt(String prompt) {
        System.out.print(prompt);
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(line);
                if (val > 0) {
                    return val;
                }
                System.out.print("Number must be greater than 0. Please try again: ");
            } catch (NumberFormatException e) {
                System.out.print("Invalid integer format. Please enter a positive integer: ");
            }
        }
    }

    private long readPositiveLong(String prompt) {
        System.out.print(prompt);
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                long val = Long.parseLong(line);
                if (val > 0) {
                    return val;
                }
                System.out.print("ID must be positive. Please try again: ");
            } catch (NumberFormatException e) {
                System.out.print("Invalid numeric format. Please enter a positive integer: ");
            }
        }
    }

    private BigDecimal readPositiveAmount(String prompt) {
        System.out.print(prompt);
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                return InputValidator.parseAmount(line);
            } catch (IllegalArgumentException e) {
                System.out.print(e.getMessage() + " Try again: ");
            }
        }
    }

    private String readNonBlankString(String prompt) {
        System.out.print(prompt);
        while (true) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.print("Input cannot be empty. Please enter a valid value: ");
        }
    }

    private LocalDate readValidDate(String prompt, boolean allowDefaultToday) {
        System.out.print(prompt);
        while (true) {
            String line = scanner.nextLine().trim();
            if (allowDefaultToday && line.isEmpty()) {
                return LocalDate.now();
            }
            try {
                return InputValidator.parseDate(line);
            } catch (IllegalArgumentException e) {
                System.out.print(e.getMessage() + " Try again: ");
            }
        }
    }

    // --- Presentation Formatting Helpers ---

    private void printExpenseTable(List<Expense> expenses) {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("%-6s | %-25s | %-15s | %-12s | %-12s%n", "ID", "Description", "Category", "Amount", "Date");
        System.out.println("--------------------------------------------------------------------------------");
        for (Expense e : expenses) {
            System.out.printf("%-6d | %-25s | %-15s | $%-11.2f | %-12s%n",
                    e.getId(),
                    truncate(e.getDescription(), 25),
                    truncate(e.getCategory(), 15),
                    e.getAmount(),
                    e.getDate());
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

    private void printExpenseRow(Expense e) {
        System.out.printf("ID: %d | Description: %s | Category: %s | Amount: $%.2f | Date: %s%n",
                e.getId(), e.getDescription(), e.getCategory(), e.getAmount(), e.getDate());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }
}
