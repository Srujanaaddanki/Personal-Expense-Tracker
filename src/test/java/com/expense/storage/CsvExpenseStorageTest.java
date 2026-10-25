package com.expense.storage;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsvExpenseStorage Unit Tests")
class CsvExpenseStorageTest {

    @TempDir
    Path tempDir;

    private CsvExpenseStorage storage;
    private Path csvFile;

    @BeforeEach
    void setUp() {
        storage = new CsvExpenseStorage();
        csvFile = tempDir.resolve("test_expenses.csv");
    }

    @Test
    @DisplayName("Round-trip persistence: Successfully saves and reloads multiple expenses")
    void testSaveAndLoadRoundTrip() {
        Expense e1 = new Expense(1L, "Groceries", "Food", new BigDecimal("45.50"), LocalDate.of(2026, 10, 15));
        Expense e2 = new Expense(2L, "Bus Pass", "Transport", new BigDecimal("30.00"), LocalDate.of(2026, 10, 20));
        Expense e3 = new Expense(3L, "Textbook", "Education", new BigDecimal("120.75"), LocalDate.of(2026, 10, 25));

        storage.saveExpenses(List.of(e1, e2, e3), csvFile);
        assertTrue(Files.exists(csvFile));

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(3, loaded.size());

        assertEquals(1L, loaded.get(0).getId());
        assertEquals("Groceries", loaded.get(0).getDescription());
        assertEquals("Food", loaded.get(0).getCategory());
        assertEquals(new BigDecimal("45.50"), loaded.get(0).getAmount());
        assertEquals(LocalDate.of(2026, 10, 15), loaded.get(0).getDate());

        assertEquals(2L, loaded.get(1).getId());
        assertEquals(3L, loaded.get(2).getId());
    }

    @Test
    @DisplayName("RFC 4180 multiline support: Preserves fields with embedded newlines across physical lines")
    void testMultilineDescriptionAcrossPhysicalLines() throws IOException {
        String multilineDesc = "Hardware parts:\n- Screw M4\n- Washer\n- Nut";
        Expense e1 = new Expense(1L, multilineDesc, "Hardware", new BigDecimal("15.20"), LocalDate.of(2026, 10, 25));

        storage.saveExpenses(List.of(e1), csvFile);

        // Verify the raw file contains literal newlines
        String rawContent = Files.readString(csvFile);
        assertTrue(rawContent.contains("\n- Screw M4\n"));

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(1, loaded.size());
        assertEquals(multilineDesc, loaded.get(0).getDescription());
    }

    @Test
    @DisplayName("RFC 4180 commas and quotes: Handles fields containing commas and escaped quotes")
    void testCommasAndEscapedQuotes() {
        String complexDesc = "15\" Monitor, IPS panel, with \"HDMI\" cable";
        Expense e1 = new Expense(1L, complexDesc, "Electronics, Office", new BigDecimal("199.99"), LocalDate.of(2026, 10, 25));

        storage.saveExpenses(List.of(e1), csvFile);

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(1, loaded.size());
        assertEquals(complexDesc, loaded.get(0).getDescription());
        assertEquals("Electronics, Office", loaded.get(0).getCategory());
    }

    @Test
    @DisplayName("Missing file: Returns an empty list safely when target file does not exist")
    void testMissingFileReturnsEmptyList() {
        Path missingPath = tempDir.resolve("non_existent_file.csv");
        List<Expense> loaded = storage.loadExpenses(missingPath);
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("Empty file: Returns an empty list when file contains 0 bytes")
    void testEmptyFileReturnsEmptyList() throws IOException {
        Files.createFile(csvFile);
        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("Header-only file: Returns an empty list when file contains only header row")
    void testHeaderOnlyFileReturnsEmptyList() throws IOException {
        Files.writeString(csvFile, CsvExpenseStorage.CSV_HEADER + System.lineSeparator());
        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    @DisplayName("Malformed header: Rejects file with missing or wrong header columns")
    void testMalformedHeaderThrows() throws IOException {
        Files.writeString(csvFile, "id,desc,amount\n1,Coffee,4.50\n");

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("Malformed CSV header"));
    }

    @Test
    @DisplayName("Malformed row: Rejects record with missing columns and reports record number")
    void testMalformedMissingColumnsThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,Lunch,Food,12.50\n"; // Only 4 columns instead of 5
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("record #2"));
        assertTrue(ex.getMessage().contains("Expected 5 columns"));
    }

    @Test
    @DisplayName("Malformed row: Rejects non-numeric or non-positive ID and reports record number")
    void testMalformedInvalidIdThrows() throws IOException {
        String contentNonNumeric = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "abc,Lunch,Food,12.50,2026-10-25\n";
        Files.writeString(csvFile, contentNonNumeric);

        PersistenceException ex1 = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex1.getMessage().contains("record #2"));
        assertTrue(ex1.getMessage().contains("Invalid numeric format for ID"));

        String contentNegative = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "-5,Lunch,Food,12.50,2026-10-25\n";
        Files.writeString(csvFile, contentNegative);

        PersistenceException ex2 = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex2.getMessage().contains("record #2"));
        assertTrue(ex2.getMessage().contains("Expense ID must be positive"));
    }

    @Test
    @DisplayName("Malformed row: Rejects non-numeric or non-positive amount")
    void testMalformedInvalidAmountThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,Lunch,Food,free,2026-10-25\n";
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("record #2"));
        assertTrue(ex.getMessage().contains("Invalid numeric format for amount"));
    }

    @Test
    @DisplayName("Malformed row: Rejects invalid date format")
    void testMalformedInvalidDateThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,Lunch,Food,10.00,25-10-2026\n"; // DD-MM-YYYY instead of YYYY-MM-DD
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("record #2"));
        assertTrue(ex.getMessage().contains("Invalid date format"));
    }

    @Test
    @DisplayName("Duplicate ID: Rejects file containing duplicate expense IDs and reports cause")
    void testMalformedDuplicateIdThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,Lunch,Food,10.00,2026-10-25\n"
                + "1,Dinner,Food,25.00,2026-10-25\n"; // Duplicate ID 1
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("record #3"));
        assertTrue(ex.getMessage().contains("Duplicate expense ID '1'"));
    }

    @Test
    @DisplayName("Malformed syntax: Rejects unclosed quotation mark at end of file")
    void testMalformedUnclosedQuoteThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,\"Unclosed quote description,Food,10.00,2026-10-25\n";
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("Unclosed quotation mark"));
    }

    @Test
    @DisplayName("Atomic save: Successfully replaces file and cleans up temporary files")
    void testAtomicSaveCleansUpTempFile() throws IOException {
        Expense e = new Expense(1L, "Item", "Category", BigDecimal.TEN, LocalDate.now());
        storage.saveExpenses(List.of(e), csvFile);

        assertTrue(Files.exists(csvFile));

        // Verify no leftover .tmp files in tempDir
        try (Stream<Path> files = Files.list(tempDir)) {
            long tmpFileCount = files.filter(p -> p.toString().endsWith(".tmp")).count();
            assertEquals(0, tmpFileCount, "Temporary files must be cleaned up");
        }
    }

    @Test
    @DisplayName("UTF-8 BOM header: Successfully parses CSV file created with Byte Order Mark")
    void testLoadFileWithUtf8BomHeader() throws IOException {
        String content = "\uFEFF" + CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "1,Coffee,Food,4.50,2026-10-25\n";
        Files.writeString(csvFile, content);

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(1, loaded.size());
        assertEquals("Coffee", loaded.get(0).getDescription());
    }

    @Test
    @DisplayName("Malformed row: Rejects record when ID equals Long.MAX_VALUE")
    void testLoadFileWithLongMaxIdThrows() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + Long.MAX_VALUE + ",Coffee,Food,4.50,2026-10-25\n";
        Files.writeString(csvFile, content);

        PersistenceException ex = assertThrows(PersistenceException.class, () -> storage.loadExpenses(csvFile));
        assertTrue(ex.getMessage().contains("Long.MAX_VALUE"));
    }

    @Test
    @DisplayName("Blank lines: Ignores empty lines between records without error")
    void testLoadFileWithBlankLinesBetweenRecords() throws IOException {
        String content = CsvExpenseStorage.CSV_HEADER + System.lineSeparator()
                + "\n"
                + "1,Coffee,Food,4.50,2026-10-25\n"
                + "\n   \n"
                + "2,Lunch,Food,15.00,2026-10-25\n"
                + "\n";
        Files.writeString(csvFile, content);

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(2, loaded.size());
        assertEquals(1L, loaded.get(0).getId());
        assertEquals(2L, loaded.get(1).getId());
    }

    @Test
    @DisplayName("RFC 4180 whitespace escaping: Preserves fields with leading or trailing spaces")
    void testFieldsWithLeadingTrailingWhitespacePreserved() {
        Expense e = new Expense(1L, "  Special Item  ", " Category ", new BigDecimal("12.50"), LocalDate.of(2026, 10, 25));
        storage.saveExpenses(List.of(e), csvFile);

        List<Expense> loaded = storage.loadExpenses(csvFile);
        assertEquals(1, loaded.size());
        assertEquals("Special Item", loaded.get(0).getDescription());
    }
}
