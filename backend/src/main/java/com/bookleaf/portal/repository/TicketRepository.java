package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Ticket queries.
 *
 * <p>The admin queue uses {@link JpaSpecificationExecutor} rather than a single
 * dynamic JPQL query: the filters are optional, and building them with Criteria
 * avoids the "could not determine data type of parameter" problem that optional
 * {@code :param is null} predicates cause on PostgreSQL.</p>
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByTicketCode(String ticketCode);

    /** Draws the next value from the database sequence used to build ticket codes. */
    @Query(value = "select nextval('ticket_code_seq')", nativeQuery = true)
    Long nextTicketSequence();

    @Query("""
            select t from Ticket t
              join fetch t.author a
              left join fetch t.book
              left join fetch t.assignedTo
            where t.author.id = :authorId
            order by t.createdAt desc
            """)
    List<Ticket> findAllForAuthor(@Param("authorId") UUID authorId);

    @Query("""
            select t from Ticket t
              join fetch t.author a
              left join fetch t.book
              left join fetch t.assignedTo
            where t.id = :id
            """)
    Optional<Ticket> findDetailedById(@Param("id") UUID id);

    long countByStatus(TicketStatus status);

    long countByStatusNotIn(List<TicketStatus> statuses);

    List<Ticket> findTop5ByStatusNotInOrderByCreatedAtAsc(List<TicketStatus> statuses);

    /** Every ticket that has not been resolved or closed — the working backlog. */
    @Query("""
            select t from Ticket t
              join fetch t.author
              left join fetch t.book
              left join fetch t.assignedTo
            where t.status not in (com.bookleaf.portal.domain.TicketStatus.RESOLVED,
                                   com.bookleaf.portal.domain.TicketStatus.CLOSED)
            """)
    List<Ticket> findAllForAdminStats();

    /** Critical and high priority tickets that are still open. */
    @Query("""
            select t from Ticket t
              join fetch t.author
              left join fetch t.book
              left join fetch t.assignedTo
            where t.status not in (com.bookleaf.portal.domain.TicketStatus.RESOLVED,
                                   com.bookleaf.portal.domain.TicketStatus.CLOSED)
              and t.priorityRank >= 3
            """)
    Page<Ticket> findUrgent(Pageable pageable);
}
