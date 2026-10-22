package com.bookleaf.portal.web;

import com.bookleaf.portal.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorTicketApiTest extends IntegrationTestBase {

    private MvcResult createTicket(String token, String subject, String description) throws Exception {
        return mockMvc.perform(withAuth(post("/api/v1/author/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", subject,
                                "description", description))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    @DisplayName("a new ticket is categorised and prioritised automatically")
    void ticketIsTriagedOnCreation() throws Exception {
        MvcResult result = createTicket(authorToken,
                "I have not received any royalty for 6 months",
                "My royalty payment has not come through for six months and I need to know when I will be paid.");
        JsonNode body = json(result);

        org.assertj.core.api.Assertions.assertThat(body.path("category").asText())
                .isEqualTo("ROYALTY_PAYMENTS");
        org.assertj.core.api.Assertions.assertThat(body.path("priority").asText())
                .isEqualTo("CRITICAL");
        org.assertj.core.api.Assertions.assertThat(body.path("status").asText()).isEqualTo("OPEN");
        // No AI provider is configured in tests, so the deterministic rules decide.
        org.assertj.core.api.Assertions.assertThat(body.path("categorySource").asText())
                .isEqualTo("HEURISTIC");
        org.assertj.core.api.Assertions.assertThat(body.path("ticketCode").asText()).startsWith("BL-");
    }

    @Test
    @DisplayName("an author only sees their own tickets")
    void authorOnlySeesOwnTickets() throws Exception {
        createTicket(authorToken, "My royalty is late", "The royalty for my book has not arrived for months.");

        mockMvc.perform(withAuth(get("/api/v1/author/tickets"), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorName").value("Author One"));

        mockMvc.perform(withAuth(get("/api/v1/author/tickets"), otherAuthorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("an author cannot read another author's ticket")
    void authorCannotReadForeignTicket() throws Exception {
        MvcResult created = createTicket(authorToken,
                "My royalty is late", "The royalty for my book has not arrived for months.");
        String ticketId = json(created).path("id").asText();

        // 404 (not 403) so an author cannot probe for the existence of other tickets.
        mockMvc.perform(withAuth(get("/api/v1/author/tickets/" + ticketId), otherAuthorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an author cannot reach admin endpoints")
    void authorCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/admin/tickets"), authorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(withAuth(get("/api/v1/admin/dashboard"), authorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a ticket cannot be raised against another author's book")
    void cannotReferenceForeignBook() throws Exception {
        var foreignBook = fixtures.bookByCode("BK002");

        mockMvc.perform(withAuth(post("/api/v1/author/tickets"), authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "bookId", foreignBook.getId().toString(),
                                "subject", "A question about this book",
                                "description", "I would like some information about this particular title please."))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("short submissions are rejected with field-level messages")
    void validationErrorsAreReported() throws Exception {
        mockMvc.perform(withAuth(post("/api/v1/author/tickets"), authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "hi",
                                "description", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subject").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    @DisplayName("the author dashboard aggregates royalty totals")
    void dashboardAggregatesRoyalties() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/author/dashboard"), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorCode").value("AUTH001"))
                .andExpect(jsonPath("$.stats.bookCount").value(1))
                .andExpect(jsonPath("$.stats.totalCopiesSold").value(342))
                .andExpect(jsonPath("$.stats.royaltyPending").value(3570.0));
    }

    @Test
    @DisplayName("the books endpoint returns royalty figures for the signed-in author only")
    void booksAreScopedToTheAuthor() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/author/books"), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookCode").value("BK001"))
                .andExpect(jsonPath("$[0].availableOn.length()").value(2));

        mockMvc.perform(withAuth(get("/api/v1/author/books"), otherAuthorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookCode").value("BK002"));
    }
}
