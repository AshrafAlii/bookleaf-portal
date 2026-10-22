package com.bookleaf.portal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * Managed Postgres providers hand out a libpq-style connection string. The
 * PostgreSQL driver needs a JDBC URL <em>and</em> does not accept the
 * {@code user:password@} userinfo syntax, so both have to be rewritten.
 */
class JdbcUrlNormalizerTest {

    private String normalise(String url) {
        return normalise(url, null, null);
    }

    private String normalise(String url, String username, String password) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", url);
        if (username != null) {
            environment.setProperty("spring.datasource.username", username);
        }
        if (password != null) {
            environment.setProperty("spring.datasource.password", password);
        }
        new JdbcUrlNormalizer().postProcessEnvironment(environment, new SpringApplication());
        return environment.getProperty("spring.datasource.url");
    }

    @Test
    @DisplayName("a libpq connection string becomes a JDBC URL")
    void convertsLibpqUri() {
        assertThat(normalise("postgresql://bookleaf:secret@db-host:5432/bookleaf"))
                .isEqualTo("jdbc:postgresql://db-host:5432/bookleaf?user=bookleaf&password=secret");
    }

    @Test
    @DisplayName("the shorter postgres:// scheme is handled too")
    void convertsPostgresScheme() {
        assertThat(normalise("postgres://user:pass@host:5432/db"))
                .isEqualTo("jdbc:postgresql://host:5432/db?user=user&password=pass");
    }

    @Test
    @DisplayName("the userinfo is dropped when the credentials are supplied separately")
    void dropsUserinfoWhenCredentialsAreSeparate() {
        // This is the Render case: DB_URL comes from the connection string and
        // DB_USERNAME / DB_PASSWORD come from the role properties.
        assertThat(normalise("postgresql://bookleaf:secret@db-host:5432/bookleaf", "bookleaf", "secret"))
                .isEqualTo("jdbc:postgresql://db-host:5432/bookleaf");
    }

    @Test
    @DisplayName("a URL that is already a JDBC URL is left alone")
    void leavesJdbcUrlAlone() {
        String jdbc = "jdbc:postgresql://localhost:5432/bookleaf";
        assertThat(normalise(jdbc)).isEqualTo(jdbc);
    }

    @Test
    @DisplayName("query parameters such as sslmode survive the conversion")
    void keepsQueryParameters() {
        assertThat(normalise("postgresql://u:p@h:5432/db?sslmode=require", "u", "p"))
                .isEqualTo("jdbc:postgresql://h:5432/db?sslmode=require");
    }

    @Test
    @DisplayName("credentials are moved into the query string when there are none")
    void movesCredentialsIntoTheQueryString() {
        assertThat(normalise("postgresql://u:p@h:5432/db"))
                .isEqualTo("jdbc:postgresql://h:5432/db?user=u&password=p");
    }

    @Test
    @DisplayName("a URL with no credentials is converted without a query string")
    void handlesUrlWithoutCredentials() {
        assertThat(normalise("postgresql://db-host:5432/bookleaf"))
                .isEqualTo("jdbc:postgresql://db-host:5432/bookleaf");
    }

    @Test
    @DisplayName("a URL that is neither form is untouched")
    void leavesOtherUrlsAlone() {
        assertThat(normalise("jdbc:h2:mem:testdb")).isEqualTo("jdbc:h2:mem:testdb");
    }
}
