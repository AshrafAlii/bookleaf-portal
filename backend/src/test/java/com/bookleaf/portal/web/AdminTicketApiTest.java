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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTicketApiTest extends IntegrationTestBase {

    private String createTicketAsAuthor(String token) throws Exception {
        MvcResult result = mockMvc.perform(withAuth(post("/api/v1/author/tickets"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subject", "Royalty has not been paid for six months",
                                "description", "I published this title and my royalty payment is still pending."))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("id").asText();
    }

    @Test
    @DisplayName("the queue lists every author's tickets and can be filtered")
    void queueListsAndFilters() throws Exception {
        createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(get("/api/v1/admin/tickets"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].authorName").value("Author One"));

        mockMvc.perform(withAuth(get("/api/v1/admin/tickets?category=ROYALTY_PAYMENTS"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(withAuth(get("/api/v1/admin/tickets?category=PRINTING_QUALITY"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(withAuth(get("/api/v1/admin/tickets?search=saffron"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("an unknown filter value returns a 400 rather than a 500")
    void unknownFilterIsRejected() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/admin/tickets?category=NOT_A_CATEGORY"), adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an admin can override the AI category and priority")
    void adminCanOverrideDecisions() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(put("/api/v1/admin/tickets/" + ticketId + "/category"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("category", "GENERAL_INQUIRY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.category").value("GENERAL_INQUIRY"))
                .andExpect(jsonPath("$.ticket.categorySource").value("MANUAL"));

        mockMvc.perform(withAuth(put("/api/v1/admin/tickets/" + ticketId + "/priority"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("priority", "LOW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.priority").value("LOW"))
                .andExpect(jsonPath("$.ticket.prioritySource").value("MANUAL"));
    }

    @Test
    @DisplayName("an admin reply is visible to the author and records the first response")
    void adminReplyIsVisibleToTheAuthor() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/" + ticketId + "/replies"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "We are investigating and will come back to you within 48 hours."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1));

        mockMvc.perform(withAuth(get("/api/v1/author/tickets/" + ticketId), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].body").value(
                        "We are investigating and will come back to you within 48 hours."))
                .andExpect(jsonPath("$.ticket.firstResponseAt").isNotEmpty());
    }

    @Test
    @DisplayName("internal notes never reach the author")
    void internalNotesAreHiddenFromTheAuthor() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/" + ticketId + "/notes"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "INTERNAL: the Q2 settlement batch has not run yet."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].internal").value(true));

        mockMvc.perform(withAuth(get("/api/v1/author/tickets/" + ticketId), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test
    @DisplayName("status changes are recorded in the audit trail")
    void statusChangesAreAudited() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(put("/api/v1/admin/tickets/" + ticketId + "/status"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"));

        JsonNode events = json(mockMvc.perform(withAuth(get("/api/v1/admin/tickets/" + ticketId), adminToken))
                .andReturn()).path("events");
        org.assertj.core.api.Assertions.assertThat(events.toString()).contains("STATUS_CHANGED");
    }

    @Test
    @DisplayName("an admin can assign a ticket to themselves")
    void adminCanAssignToSelf() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/" + ticketId + "/assign"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.assignedToName").value("BookLeaf Ops"));
    }

    @Test
    @DisplayName("draft generation degrades gracefully when no AI provider is configured")
    void draftDegradesGracefully() throws Exception {
        String ticketId = createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/" + ticketId + "/draft"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.note").isNotEmpty())
                .andExpect(jsonPath("$.contextTokens").isNumber());
    }

    @Test
    @DisplayName("the admin dashboard reports queue health")
    void adminDashboardReportsQueueHealth() throws Exception {
        createTicketAsAuthor(authorToken);

        mockMvc.perform(withAuth(get("/api/v1/admin/dashboard"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.open").value(1))
                .andExpect(jsonPath("$.queue.awaitingFirstResponse").value(1))
                .andExpect(jsonPath("$.totalAuthors").value(2));
    }
}
