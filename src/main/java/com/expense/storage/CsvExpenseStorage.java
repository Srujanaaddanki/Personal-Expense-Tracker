package com.expense.storage;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles persistent storage of expenses to and from a local CSV file.
 *
 * <p>Key Architecture and Reliability Features:
 * <ul>
 *   <li><b>Full RFC 4180 Compliance:</b> Supports commas, escaped quotation marks ({@code ""}),
 *       and embedded newlines across multiple physical lines within quoted fields.</li>
 *   <li><b>Whole-File Validation:</b> The entire file is validated upfront before returning.
 *       If any record contains invalid data, corrupt syntax, or duplicate IDs, loading aborts
 *       completely, leaving in-memory data untouched and preventing corrupted file overwriting.</li>
 *   <li><b>Atomic Replacement:</b> Writes are performed first to a temporary file in the same
 *       parent directory, then atomically moved via {@link StandardCopyOption#ATOMIC_MOVE}.
 *       If atomic move is unsupported on the filesystem, it safely falls back to standard replacement.</li>
 *   <li><b>Temporary File Cleanup:</b> Temporary files are safely cleaned up in all failure paths.</li>
 * </ul>
 */
public class CsvExpenseStorage {

    public static final String CSV_HEADER = "id,description,category,amount,date";

    /**
     * Loads and validates all expenses from the specified CSV file.
     *
     * @param path The path of the CSV file to load
     * @return An unmodifiable list of validated expenses, or an empty list if file is missing or empty
     * @throws PersistenceException if the CSV is malformed, contains duplicate IDs, or cannot be read
     */
    public List<Expense> loadExpenses(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null.");
        }

        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        List<List<String>> rawRecords;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            rawRecords = parseCsvRecords(reader);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read CSV file from " + path + ": " + e.getMessage(), e);
        }

        if (rawRecords.isEmpty()) {
            return Collections.emptyList();
        }

        // Validate header
        List<String> header = rawRecords.get(0);
        validateHeader(header);

        if (rawRecords.size() == 1) {
            // Header-only file
            return Collections.emptyList();
        }

        // Whole-file validation
        List<Expense> validatedExpenses = new ArrayList<>(rawRecords.size() - 1);
        Set<Long> seenIds = new HashSet<>();

        for (int i = 1; i < rawRecords.size(); i++) {
            int recordNumber = i + 1; // 1-based record number in file
            List<String> row = rawRecords.get(i);
            Expense expense = validateAndParseRecord(row, recordNumber, seenIds);
            validatedExpenses.add(expense);
        }

        return Collections.unmodifiableList(validatedExpenses);
    }

    /**
     * Persists expenses to a CSV file using atomic temporary file replacement.
     *
     * @param expenses The list of expenses to save
     * @param targetPath The destination file path
     * @throws PersistenceException if an I/O error prevents writing or replacing the file
     */
    public void saveExpenses(List<Expense> expenses, Path targetPath) {
        if (targetPath == null) {
            throw new IllegalArgumentException("Target path cannot be null.");
        }
        if (expenses == null) {
            throw new IllegalArgumentException("Expenses list cannot be null.");
        }

        Path absTarget = targetPath.toAbsolutePath();
        Path parentDir = absTarget.getParent();
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new PersistenceException("Failed to create parent directory: " + parentDir, e);
            }
        }

        Path tempPath;
        try {
            tempPath = (parentDir != null)
                    ? Files.createTempFile(parentDir, "expenses_", ".tmp")
                    : Files.createTempFile("expenses_", ".tmp");
        } catch (IOException e) {
            throw new PersistenceException("Failed to create temporary file for saving", e);
        }

        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                writer.write(CSV_HEADER);
                writer.newLine();
                for (Expense e : expenses) {
                    writer.write(String.format(Locale.ROOT, "%d,%s,%s,%s,%s",
                            e.getId(),
                            escapeField(e.getDescription()),
                            escapeField(e.getCategory()),
                            e.getAmount().toPlainString(),
                            e.getDate().toString()
                    ));
                    writer.newLine();
                }
                writer.flush();
            }

            // Attempt atomic move first
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback to non-atomic replace move when atomic move is not supported by the underlying OS/filesystem
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new PersistenceException("Failed to persist expenses to " + targetPath + ": " + e.getMessage(), e);
        } finally {
            // Clean up temporary file if it still exists (e.g., in case of write or move failure)
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // Best-effort cleanup
            }
        }
    }

    /**
     * Parses a CSV character stream according to RFC 4180, supporting multiline records across physical lines.
     *
     * @param reader The input reader
     * @return List of records, where each record is a list of column string values
     * @throws IOException if a low-level I/O error occurs
     * @throws PersistenceException if unclosed quotes are found
     */
    public static List<List<String>> parseCsvRecords(Reader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        int ch;

        while ((ch = br.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    br.mark(1);
                    int nextCh = br.read();
                    if (nextCh == '"') {
                        // Escaped quote: "" -> "
                        currentField.append('"');
                    } else {
                        // End of quoted field
                        inQuotes = false;
                        if (nextCh != -1) {
                            br.reset();
                        }
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    br.mark(1);
                    int nextCh = br.read();
                    if (nextCh != '\n' && nextCh != -1) {
                        br.reset();
                    }
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isEmptyRecord(currentRecord)) {
                        records.add(currentRecord);
                    }
                    currentRecord = new ArrayList<>();
                } else if (c == '\n') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    if (!isEmptyRecord(currentRecord)) {
                        records.add(currentRecord);
                    }
                    currentRecord = new ArrayList<>();
                } else {
                    currentField.append(c);
                }
            }
        }

        if (inQuotes) {
            throw new PersistenceException("Malformed CSV: Unclosed quotation mark detected at end of file.");
        }

        if (currentField.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(currentField.toString());
            if (!isEmptyRecord(currentRecord)) {
                records.add(currentRecord);
            }
        }

        return records;
    }

    /**
     * Escapes a single string field according to RFC 4180.
     *
     * @param value Raw string field
     * @return Escaped string field wrapped in double quotes if commas, quotes, or newlines are present
     */
    public static String escapeField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
                || value.startsWith(" ") || value.endsWith(" ")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void validateHeader(List<String> header) {
        if (header.isEmpty()) {
            throw new PersistenceException("Malformed CSV header: Header row is empty.");
        }
        String firstCol = header.get(0).trim();
        if (firstCol.startsWith("\uFEFF")) {
            firstCol = firstCol.substring(1).trim();
        }
        if (header.size() != 5
                || !"id".equalsIgnoreCase(firstCol)
                || !"description".equalsIgnoreCase(header.get(1).trim())
                || !"category".equalsIgnoreCase(header.get(2).trim())
                || !"amount".equalsIgnoreCase(header.get(3).trim())
                || !"date".equalsIgnoreCase(header.get(4).trim())) {
            throw new PersistenceException("Malformed CSV header: Expected 'id,description,category,amount,date' but found: "
                    + String.join(",", header));
        }
    }

    private Expense validateAndParseRecord(List<String> row, int recordNumber, Set<Long> seenIds) {
        if (row.size() != 5) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Expected 5 columns but found " + row.size());
        }

        // Validate ID
        String idStr = row.get(0).trim();
        long id;
        try {
            id = Long.parseLong(idStr);
            if (id <= 0) {
                throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                        + ": Expense ID must be positive, found '" + idStr + "'.");
            }
            if (id == Long.MAX_VALUE) {
                throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                        + ": Expense ID has reached Long.MAX_VALUE (" + id + "), which prevents further ID allocation.");
            }
        } catch (NumberFormatException e) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Invalid numeric format for ID '" + idStr + "'.", e);
        }

        if (!seenIds.add(id)) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Duplicate expense ID '" + id + "' found.");
        }

        // Validate Description
        String description = row.get(1).trim();
        if (description.isEmpty()) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Description cannot be empty or blank.");
        }

        // Validate Category
        String category = row.get(2).trim();
        if (category.isEmpty()) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Category cannot be empty or blank.");
        }

        // Validate Amount
        String amountStr = row.get(3).trim();
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                        + ": Amount must be strictly positive, found '" + amountStr + "'.");
            }
        } catch (NumberFormatException e) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Invalid numeric format for amount '" + amountStr + "'.", e);
        }

        // Validate Date
        String dateStr = row.get(4).trim();
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new PersistenceException("Malformed CSV record at record #" + recordNumber
                    + ": Invalid date format '" + dateStr + "' (expected YYYY-MM-DD).", e);
        }

        return new Expense(id, description, category, amount, date);
    }

    private static boolean isEmptyRecord(List<String> record) {
        if (record.isEmpty()) {
            return true;
        }
        if (record.size() == 1 && record.get(0).trim().isEmpty()) {
            return true;
        }
        return false;
    }
}
