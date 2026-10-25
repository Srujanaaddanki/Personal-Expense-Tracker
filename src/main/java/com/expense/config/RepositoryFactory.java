package com.expense.config;

import com.expense.repository.CsvExpenseRepository;
import com.expense.repository.ExpenseRepository;
import com.expense.repository.PostgresExpenseRepository;
import com.expense.storage.CsvExpenseStorage;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Factory class providing repository instances based on configuration.
 *
 * <p>Supported storage modes:
 * <ul>
 *   <li>{@code "csv"} (Default): Uses local CSV file persistence.</li>
 *   <li>{@code "postgres"}: Uses PostgreSQL relational database persistence via Spring JDBC.</li>
 * </ul>
 */
public final class RepositoryFactory {

    public static final String STORAGE_TYPE_CSV = "csv";
    public static final String STORAGE_TYPE_POSTGRES = "postgres";
    public static final Path DEFAULT_CSV_PATH = Path.of("data", "expenses.csv");

    private RepositoryFactory() {
    }

    /**
     * Creates an {@link ExpenseRepository} based on the {@code STORAGE_TYPE} environment variable.
     * Defaults to CSV persistence if unspecified.
     *
     * @return Configured {@link ExpenseRepository} implementation
     */
    public static ExpenseRepository createRepository() {
        String storageType = System.getenv("STORAGE_TYPE");
        if (storageType == null || storageType.trim().isEmpty()) {
            storageType = System.getProperty("storage.type", STORAGE_TYPE_CSV);
        }
        return createRepository(storageType);
    }

    /**
     * Creates an {@link ExpenseRepository} for a specified storage type name.
     *
     * @param storageType Storage type (e.g. {@code "csv"} or {@code "postgres"})
     * @return Initialized repository instance
     */
    public static ExpenseRepository createRepository(String storageType) {
        String normalized = (storageType != null) ? storageType.trim().toLowerCase(Locale.ROOT) : STORAGE_TYPE_CSV;

        if (STORAGE_TYPE_POSTGRES.equals(normalized)) {
            DataSource ds = DatabaseConfig.createDataSourceFromEnv();
            return new PostgresExpenseRepository(ds);
        }

        return createCsvRepository(DEFAULT_CSV_PATH);
    }

    /**
     * Creates a CSV-backed {@link ExpenseRepository} pointing to the specified target path.
     *
     * @param dataPath Target CSV file path
     * @return {@link CsvExpenseRepository}
     */
    public static CsvExpenseRepository createCsvRepository(Path dataPath) {
        return new CsvExpenseRepository(new CsvExpenseStorage(), dataPath);
    }

    /**
     * Creates a PostgreSQL-backed {@link ExpenseRepository} using the supplied {@link DataSource}.
     *
     * @param dataSource Configured SQL datasource
     * @return {@link PostgresExpenseRepository}
     */
    public static PostgresExpenseRepository createPostgresRepository(DataSource dataSource) {
        return new PostgresExpenseRepository(dataSource);
    }
}
