package com.bookleaf.portal.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Manages the PostgreSQL database used by the integration tests.
 *
 * <p>Tests run against a real PostgreSQL with the Flyway migrations applied, so
 * the schema under test is exactly the schema that ships. Using a real database
 * rather than an in-memory substitute also means dialect-specific behaviour
 * (UUID generation, sequences, numeric handling) is genuinely exercised.</p>
 *
 * <p>The database is created on demand and the tables are truncated between
 * tests, so every test starts from a known state. Point the tests at a
 * different server with {@code TEST_DB_URL}.</p>
 */
public final class TestDatabase {

    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/bookleaf_test";
    private static final String USER = envOrDefault("TEST_DB_USERNAME", "bookleaf");
    private static final String PASSWORD = envOrDefault("TEST_DB_PASSWORD", "bookleaf");

    private TestDatabase() {
    }

    public static String url() {
        return envOrDefault("TEST_DB_URL", DEFAULT_URL);
    }

    /**
     * Creates the test database if it does not exist yet.
     */
    public static void ensureExists() {
        String url = url();
        String database = databaseName(url);
        String maintenanceUrl = url.replace("/" + database, "/postgres");
        try (Connection connection = DriverManager.getConnection(maintenanceUrl, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            boolean exists;
            try (ResultSet rs = statement.executeQuery(
                    "select 1 from pg_database where datname = '" + database + "'")) {
                exists = rs.next();
            }
            if (!exists) {
                statement.execute("create database \"" + database + "\"");
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not prepare the test database " + database + " at " + maintenanceUrl
                            + ". Start PostgreSQL (for example `docker compose up -d db`) and try again.", ex);
        }
    }

    /**
     * Removes all rows so each test starts clean. The schema is left untouched —
     * Flyway owns it.
     */
    public static void reset() {
        try (Connection connection = DriverManager.getConnection(url(), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    truncate table ticket_message, ticket_event, ai_cache_entry, ai_usage_log,
                                      ticket, book_platform, book, author, app_user restart identity cascade
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reset the test database", ex);
        }
    }

    private static String databaseName(String url) {
        int slash = url.lastIndexOf('/');
        String tail = slash >= 0 ? url.substring(slash + 1) : url;
        int query = tail.indexOf('?');
        if (query >= 0) {
            tail = tail.substring(0, query);
        }
        return tail.isBlank() ? "bookleaf_test" : tail;
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
