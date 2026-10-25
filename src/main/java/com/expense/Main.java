package com.expense;

import com.expense.model.Expense;
import com.expense.service.ExpenseService;
import com.expense.util.InputValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    public Main(Scanner scanner, ExpenseService expenseService) {
        this.scanner = scanner;
        this.expenseService = expenseService;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ExpenseService service = new ExpenseService();
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

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readMenuChoice(1, 7);

            switch (choice) {
                case 1 -> handleAddExpense();
                case 2 -> handleViewAllExpenses();
                case 3 -> handleSearchExpenseById();
                case 4 -> handleUpdateExpense();
                case 5 -> handleDeleteExpense();
                case 6 -> handleCalculateTotalSpending();
                case 7 -> {
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
        System.out.println("4. Update an Expense");
        System.out.println("5. Delete an Expense");
        System.out.println("6. Calculate Total Spending");
        System.out.println("7. Exit");
        System.out.print("Please enter your choice (1-7): ");
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
        } catch (IllegalArgumentException e) {
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
        } catch (IllegalArgumentException e) {
            System.out.println("\n[ERROR] Failed to update expense: " + e.getMessage());
        }
    }

    private void handleDeleteExpense() {
        System.out.println("\n--- Delete an Expense ---");
        long id = readPositiveLong("Enter Expense ID to delete: ");

        boolean deleted = expenseService.deleteExpense(id);
        if (deleted) {
            System.out.println("\n[SUCCESS] Expense with ID " + id + " was deleted.");
        } else {
            System.out.println("\n[NOT FOUND] No expense exists with ID: " + id);
        }
    }

    private void handleCalculateTotalSpending() {
        System.out.println("\n--- Total Spending Summary ---");
        BigDecimal total = expenseService.calculateTotalSpending();
        int count = expenseService.getExpenseCount();

        System.out.printf("Total Recorded Expenses: %d%n", count);
        System.out.printf("Total Amount Spent:      $%.2f%n", total);
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
