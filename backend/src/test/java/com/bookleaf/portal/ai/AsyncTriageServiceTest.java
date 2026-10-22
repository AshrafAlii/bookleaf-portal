package com.bookleaf.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookleaf.portal.domain.DecisionSource;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.service.TicketCommandService;
import com.bookleaf.portal.service.TicketEventPublisher;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Tests for the background refinement.
 *
 * <p>The executor is replaced with a stub that runs the submitted task inline,
 * so the test can observe exactly what the background thread would do.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncTriageServiceTest {

    @Mock
    private ThreadPoolTaskExecutor executor;

    @Mock
    private AiTriageService triageService;

    @Mock
    private TicketCommandService commandService;

    @Mock
    private TicketEventPublisher eventPublisher;

    @Mock
    private Ticket ticket;

    private AsyncTriageService service() {
        // Run the submitted task inline instead of on a background thread.
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        return new AsyncTriageService(executor, triageService, commandService, eventPublisher);
    }

    @Test
    @DisplayName("the refinement runs off the caller's thread")
    void refinementIsSubmittedToTheExecutor() {
        when(commandService.loadForAdmin(any())).thenReturn(ticket);
        when(triageService.triage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TriageResult.of(TicketCategory.PRINTING_QUALITY, TicketPriority.HIGH,
                        "defective copies", DecisionSource.AI, java.util.List.of(), null));

        service().refine(UUID.randomUUID(), "Print quality", "blurry pages", "Book", "ctx");

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("when the AI disagrees, the ticket is updated and an event is published")
    void disagreementUpdatesTheTicket() {
        UUID ticketId = UUID.randomUUID();
        when(commandService.loadForAdmin(ticketId)).thenReturn(ticket);
        when(triageService.triage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TriageResult.of(TicketCategory.PRINTING_QUALITY, TicketPriority.HIGH,
                        "defective copies", DecisionSource.AI, java.util.List.of(), null));
        when(commandService.applyAiDecision(any(), any(), any(), anyString())).thenReturn(ticket);
        when(ticket.getTicketCode()).thenReturn("BL-000001");

        service().refine(ticketId, "Print quality", "blurry pages", "Book", "ctx");

        verify(commandService).applyAiDecision(any(Ticket.class),
                org.mockito.ArgumentMatchers.eq(TicketCategory.PRINTING_QUALITY),
                org.mockito.ArgumentMatchers.eq(TicketPriority.HIGH),
                anyString());
        verify(eventPublisher).publish(any(Ticket.class), org.mockito.ArgumentMatchers.eq("AI_CLASSIFIED"),
                anyString());
    }

    @Test
    @DisplayName("when the provider is unavailable the deterministic classification stands")
    void providerFailureLeavesTheTicketAlone() {
        UUID ticketId = UUID.randomUUID();
        when(commandService.loadForAdmin(ticketId)).thenReturn(ticket);
        when(triageService.triage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(TriageResult.of(TicketCategory.ROYALTY_PAYMENTS, TicketPriority.MEDIUM,
                        "rule match", DecisionSource.HEURISTIC, java.util.List.of(),
                        "The AI service was unavailable."));

        service().refine(ticketId, "Royalty", "late", "Book", "ctx");

        verify(commandService, org.mockito.Mockito.never()).applyAiDecision(any(), any(), any(), anyString());
        verify(commandService).recordEvent(any(Ticket.class), any(),
                org.mockito.ArgumentMatchers.eq("AI_CLASSIFIED"), anyString());
    }
}
