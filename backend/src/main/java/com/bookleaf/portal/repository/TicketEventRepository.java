package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.TicketEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

    List<TicketEvent> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
