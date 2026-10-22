package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composable predicates for the admin ticket queue.
 *
 * <p>Each filter is a {@link Specification} that is a no-op when its value is
 * {@code null}, so the controller can pass optional query parameters straight
 * through without branching.</p>
 */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasCategory(TicketCategory category) {
        return (root, query, cb) -> category == null ? cb.conjunction() : cb.equal(root.get("category"), category);
    }

    public static Specification<Ticket> hasPriority(TicketPriority priority) {
        return (root, query, cb) -> priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> assignedTo(UUID userId) {
        return (root, query, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.get("assignedTo").get("id"), userId);
    }

    public static Specification<Ticket> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Free-text search across the subject, description, ticket code and author name.
     */
    public static Specification<Ticket> matchesText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
            Join<Ticket, com.bookleaf.portal.domain.Author> author = root.join("author", JoinType.INNER);
            return cb.or(
                    cb.like(cb.lower(root.get("subject")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("ticketCode")), pattern),
                    cb.like(cb.lower(author.get("name")), pattern),
                    cb.like(cb.lower(author.get("email")), pattern));
        };
    }
}
