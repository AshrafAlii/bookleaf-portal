package com.bookleaf.portal.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Normalises a libpq-style PostgreSQL connection string into a JDBC URL.
 *
 * <p>Several managed Postgres providers — Render, Heroku, Railway, Fly — hand
 * out the connection string as a libpq URI:</p>
 *
 * <pre>postgresql://user:password@host:5432/database</pre>
 *
 * <p>Spring Boot's JDBC support needs the JDBC form, and the PostgreSQL driver
 * additionally does <em>not</em> accept the {@code user:password@} userinfo
 * syntax — it expects the credentials as {@code ?user=...&password=...}. Both
 * forms therefore have to be rewritten, or the datasource fails with an opaque
 * error:</p>
 *
 * <ul>
 *   <li>{@code PropertiesJdbcConnectionDetails.getDriverClassName} — no driver can be inferred</li>
 *   <li>{@code UnknownHostException: user:password@host} — the driver treats the
 *       userinfo as part of the hostname</li>
 * </ul>
 *
 * <p>Credentials are passed as separate properties wherever they are available
 * ({@code DB_USERNAME} / {@code DB_PASSWORD}); otherwise the userinfo is moved
 * into the query string, which the driver does understand.</p>
 */
public class JdbcUrlNormalizer implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JdbcUrlNormalizer.class);

    private static final String SOURCE_NAME = "bookleafJdbcUrlNormalizer";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new LinkedHashMap<>();

        normalise(environment, overrides, "spring.datasource.url");
        // Also fix the raw variable, so anything else reading it sees a JDBC URL.
        normalise(environment, overrides, "DB_URL");

        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, overrides));
        }
    }

    private void normalise(ConfigurableEnvironment environment, Map<String, Object> overrides, String key) {
        String url = environment.getProperty(key);
        if (url == null) {
            return;
        }
        String trimmed = url.trim();
        if (!isLibpqUri(trimmed)) {
            return;
        }

        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("Could not parse the host from the database URL; leaving it unchanged");
                return;
            }

            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
            if (uri.getPort() > 0) {
                jdbc.append(':').append(uri.getPort());
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            jdbc.append(path.isBlank() ? "" : path);

            String query = uri.getQuery();
            if (query != null && !query.isBlank()) {
                jdbc.append('?').append(query);
            }

            // The driver does not understand userinfo in the URL. Move it into the
            // query string unless the credentials are supplied separately.
            String userInfo = uri.getUserInfo();
            boolean haveSeparateCredentials =
                    hasValue(environment.getProperty("spring.datasource.username"))
                            || hasValue(environment.getProperty("DB_USERNAME"))
                            || hasValue(environment.getProperty("spring.datasource.password"))
                            || hasValue(environment.getProperty("DB_PASSWORD"));

            if (userInfo != null && !userInfo.isBlank() && !haveSeparateCredentials) {
                int separator = userInfo.indexOf(':');
                String user = separator > 0 ? userInfo.substring(0, separator) : userInfo;
                String password = separator > 0 ? userInfo.substring(separator + 1) : null;
                jdbc.append(jdbc.indexOf("?") > 0 ? '&' : '?')
                        .append("user=").append(encode(user));
                if (password != null && !password.isEmpty()) {
                    jdbc.append("&password=").append(encode(password));
                }
            }

            overrides.put(key, jdbc.toString());
            log.info("Normalised the database connection string to a JDBC URL ({} -> {})",
                    sanitise(trimmed), jdbc);
        } catch (Exception ex) {
            log.warn("Could not normalise the database connection string: {}", ex.getMessage());
        }
    }

    private boolean isLibpqUri(String url) {
        return url.regionMatches(true, 0, "postgresql://", 0, "postgresql://".length())
                || url.regionMatches(true, 0, "postgres://", 0, "postgres://".length());
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /** Removes the credentials so they never reach the log. */
    private String sanitise(String url) {
        return url.replaceAll("//[^@/]*@", "//***@");
    }

    @Override
    public int getOrder() {
        // After the config data (application.yml / .env) is loaded, but before
        // anything binds the datasource.
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
