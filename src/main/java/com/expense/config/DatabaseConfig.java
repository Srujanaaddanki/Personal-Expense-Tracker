package com.expense.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configuration provider for PostgreSQL database connections using HikariCP.
 *
 * <p>Reads environment variables following 12-factor application design principles:
 * <ul>
 *   <li>{@code DB_URL} - Target JDBC connection URL (default: {@value #DEFAULT_DB_URL})</li>
 *   <li>{@code DB_USERNAME} - Database username (default: {@value #DEFAULT_DB_USER})</li>
 *   <li>{@code DB_PASSWORD} - Database password (default: {@value #DEFAULT_DB_PASSWORD})</li>
 *   <li>{@code DB_POOL_SIZE} - Maximum connection pool size (default: {@value #DEFAULT_POOL_SIZE})</li>
 * </ul>
 */
public final class DatabaseConfig {

    public static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/expense_tracker";
    public static final String DEFAULT_DB_USER = "postgres";
    public static final String DEFAULT_DB_PASSWORD = "postgres";
    public static final int DEFAULT_POOL_SIZE = 10;

    private DatabaseConfig() {
    }

    /**
     * Creates a {@link HikariDataSource} populated from environment variables with safe defaults.
     *
     * @return Configured {@link HikariDataSource} connection pool
     */
    public static HikariDataSource createDataSourceFromEnv() {
        String url = getEnvOrDefault("DB_URL", DEFAULT_DB_URL);
        String username = getEnvOrDefault("DB_USERNAME", DEFAULT_DB_USER);
        String password = getEnvOrDefault("DB_PASSWORD", DEFAULT_DB_PASSWORD);
        int poolSize = parsePoolSize(System.getenv("DB_POOL_SIZE"), DEFAULT_POOL_SIZE);

        return createDataSource(url, username, password, poolSize);
    }

    /**
     * Creates a {@link HikariDataSource} with explicit configuration parameters.
     *
     * @param url         JDBC database URL
     * @param username    Database username
     * @param password    Database password
     * @param maxPoolSize Maximum connection pool size
     * @return Initialized {@link HikariDataSource}
     */
    public static HikariDataSource createDataSource(String url, String username, String password, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize > 0 ? maxPoolSize : DEFAULT_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setPoolName("ExpenseTrackerPool");

        return new HikariDataSource(config);
    }

    private static String getEnvOrDefault(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    private static int parsePoolSize(String envVal, int fallback) {
        if (envVal == null || envVal.trim().isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(envVal.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
