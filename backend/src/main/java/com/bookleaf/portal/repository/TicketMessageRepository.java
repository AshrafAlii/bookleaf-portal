package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.TicketMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    List<TicketMessage> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(UUID ticketId);

    long countByTicketId(UUID ticketId);

    /**
     * Loads every message for a set of tickets in one query so the queue can show
     * message counts and the latest reply without an N+1 lookup per row.
     */
    @Query("select m from TicketMessage m where m.ticket.id in :ticketIds order by m.createdAt asc")
    List<TicketMessage> findAllForTickets(@Param("ticketIds") List<UUID> ticketIds);
}
