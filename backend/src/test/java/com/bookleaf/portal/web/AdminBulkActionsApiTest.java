package com.bookleaf.portal.web;

import com.bookleaf.portal.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the bulk queue actions and saved filters — the two features that make
 * the queue usable when an ops team is working a backlog in batches.
 */
class AdminBulkActionsApiTest extends IntegrationTestBase {

    private List<String> createTickets(int count) throws Exception {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            MvcResult result = mockMvc.perform(withAuth(post("/api/v1/author/tickets"), authorToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "subject", "Royalty not received for 6 months " + i,
                                    "description", "My royalty payment has not arrived for six months now."))))
                    .andExpect(status().isCreated())
                    .andReturn();
            ids.add(json(result).path("id").asText());
        }
        return ids;
    }

    @Test
    @DisplayName("a bulk action applies to every selected ticket")
    void bulkAppliesToAllSelected() throws Exception {
        List<String> ids = createTickets(3);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", ids,
                                "status", "IN_PROGRESS",
                                "assignToMe", true,
                                "note", "Triaged in the morning batch."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.updated").value(3))
                .andExpect(jsonPath("$.statusChanged").value(3))
                .andExpect(jsonPath("$.assigned").value(3))
                .andExpect(jsonPath("$.noted").value(3))
                .andExpect(jsonPath("$.ticketIds.length()").value(3));
    }

    @Test
    @DisplayName("a bulk action writes an audit event on every ticket it touches")
    void bulkIsAuditedPerTicket() throws Exception {
        List<String> ids = createTickets(2);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", ids, "status", "RESOLVED"))))
                .andExpect(status().isOk());

        for (String id : ids) {
            JsonNode events = json(mockMvc.perform(withAuth(get("/api/v1/admin/tickets/" + id), adminToken))
                    .andReturn()).path("events");
            org.assertj.core.api.Assertions.assertThat(events.toString()).contains("STATUS_CHANGED");
            org.assertj.core.api.Assertions.assertThat(events.toString()).contains("bulk action");
        }
    }

    @Test
    @DisplayName("tickets already in the target state are left alone")
    void bulkSkipsUnchangedTickets() throws Exception {
        List<String> ids = createTickets(1);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", ids, "status", "OPEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusChanged").value(0))
                .andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    @DisplayName("a bulk note is internal and never reaches the author")
    void bulkNotesStayInternal() throws Exception {
        List<String> ids = createTickets(1);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", ids, "note", "INTERNAL: waiting on the settlement batch."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noted").value(1));

        mockMvc.perform(withAuth(get("/api/v1/author/tickets/" + ids.get(0)), authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test
    @DisplayName("a bulk request with no tickets or no action is rejected")
    void bulkValidatesItsInput() throws Exception {
        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ticketIds", List.of()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", createTickets(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose at least one action to apply"));
    }

    @Test
    @DisplayName("an author cannot run bulk actions")
    void authorCannotRunBulkActions() throws Exception {
        List<String> ids = createTickets(1);

        mockMvc.perform(withAuth(post("/api/v1/admin/tickets/bulk"), authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ticketIds", ids, "status", "CLOSED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("saved filters can be created, listed, applied and deleted")
    void savedFilterLifecycle() throws Exception {
        mockMvc.perform(withAuth(post("/api/v1/admin/filters"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Overdue royalties",
                                "filters", "{\"status\":\"OPEN\",\"category\":\"ROYALTY_PAYMENTS\"}"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Overdue royalties"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mockMvc.perform(withAuth(get("/api/v1/admin/filters"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Overdue royalties"));

        JsonNode list = json(mockMvc.perform(withAuth(get("/api/v1/admin/filters"), adminToken)).andReturn());
        String id = list.get(0).path("id").asText();

        mockMvc.perform(withAuth(delete("/api/v1/admin/filters/" + id), adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(withAuth(get("/api/v1/admin/filters"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("two saved filters cannot share a name")
    void duplicateSavedFilterNameIsRejected() throws Exception {
        Map<String, Object> payload = Map.of("name", "Same name", "filters", "{}");

        mockMvc.perform(withAuth(post("/api/v1/admin/filters"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(withAuth(post("/api/v1/admin/filters"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("saved filters are private to the admin who created them")
    void savedFiltersArePrivate() throws Exception {
        mockMvc.perform(withAuth(post("/api/v1/admin/filters"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Ops only", "filters", "{}"))))
                .andExpect(status().isCreated());

        mockMvc.perform(withAuth(get("/api/v1/admin/filters"), otherAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
