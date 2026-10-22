package com.bookleaf.portal.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base class for integration tests.
 *
 * <p>Each test runs against a real PostgreSQL with the Flyway migrations
 * applied, so the schema under test is exactly the schema that ships. The demo
 * seeder is disabled so each test owns its data.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
public abstract class IntegrationTestBase {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        TestDatabase.ensureExists();
        registry.add("spring.datasource.url", TestDatabase::url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "bookleaf"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "bookleaf"));
        registry.add("app.seed.enabled", () -> "false");
        // Integration tests must not depend on an external LLM: switch the AI layer
        // off so the suite is fast and deterministic, and covers the fallback path.
        // The AI prompt and parsing logic are unit tested separately with a stub.
        registry.add("app.ai.provider", () -> "none");
        registry.add("app.jwt.secret", () -> "integration-test-secret-key-at-least-32-characters");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestFixtures.FixtureFactory fixtures;

    protected String authorToken;
    protected String otherAuthorToken;
    protected String adminToken;
    protected String otherAdminToken;

    protected static final String AUTHOR_EMAIL = "author.one@email.com";
    protected static final String OTHER_AUTHOR_EMAIL = "author.two@email.com";
    protected static final String ADMIN_EMAIL = "ops@bookleaf.in";
    protected static final String OTHER_ADMIN_EMAIL = "lead@bookleaf.in";
    protected static final String PASSWORD = "Test@1234";

    @BeforeEach
    void seedFixtures() throws Exception {
        TestDatabase.reset();
        fixtures.createAdmin(ADMIN_EMAIL, "BookLeaf Ops", PASSWORD);
        fixtures.createAdmin(OTHER_ADMIN_EMAIL, "Priya Menon", PASSWORD);
        var author = fixtures.createAuthor("AUTH001", "Author One", AUTHOR_EMAIL, PASSWORD);
        fixtures.createBook(author, "BK001", "Whispers of the Ganges", "Published & Live",
                new java.math.BigDecimal("399"));
        var other = fixtures.createAuthor("AUTH002", "Author Two", OTHER_AUTHOR_EMAIL, PASSWORD);
        fixtures.createBook(other, "BK002", "The Saffron Diaries", "Published & Live",
                new java.math.BigDecimal("450"));

        authorToken = login(AUTHOR_EMAIL);
        otherAuthorToken = login(OTHER_AUTHOR_EMAIL);
        adminToken = login(ADMIN_EMAIL);
        otherAdminToken = login(OTHER_ADMIN_EMAIL);
    }

    protected String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", PASSWORD))))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("accessToken").asText();
    }

    protected MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
