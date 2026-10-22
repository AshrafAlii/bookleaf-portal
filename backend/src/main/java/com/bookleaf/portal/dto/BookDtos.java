package com.bookleaf.portal.dto;

import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Author-facing book and dashboard payloads.
 */
public final class BookDtos {

    private BookDtos() {
    }

    /**
     * A book as shown to its author.
     *
     * <p>Nullable fields are genuinely nullable: a title still in production has
     * no MRP or royalty rate yet, and we say so rather than showing a fake zero.</p>
     */
    public record BookResponse(UUID id,
                                String bookCode,
                                String title,
                                String isbn,
                                String genre,
                                LocalDate publicationDate,
                                String status,
                                boolean inProduction,
                                BigDecimal mrp,
                                BigDecimal authorRoyaltyPerCopy,
                                int totalCopiesSold,
                                BigDecimal totalRoyaltyEarned,
                                BigDecimal royaltyPaid,
                                BigDecimal royaltyPending,
                                LocalDate lastRoyaltyPayoutDate,
                                String printPartner,
                                List<String> availableOn) {

        public static BookResponse from(Book book) {
            return new BookResponse(
                    book.getId(),
                    book.getBookCode(),
                    book.getTitle(),
                    book.getIsbn(),
                    book.getGenre(),
                    book.getPublicationDate(),
                    book.getStatus(),
                    book.isInProduction(),
                    book.getMrp(),
                    book.getAuthorRoyaltyPerCopy(),
                    book.getTotalCopiesSold(),
                    book.getTotalRoyaltyEarned(),
                    book.getRoyaltyPaid(),
                    book.getRoyaltyPending(),
                    book.getLastRoyaltyPayoutDate(),
                    book.getPrintPartner(),
                    List.copyOf(book.getAvailableOn()));
        }
    }

    public record AuthorStats(int bookCount,
                               int liveBookCount,
                               int inProductionBookCount,
                               int totalCopiesSold,
                               BigDecimal totalRoyaltyEarned,
                               BigDecimal royaltyPaid,
                               BigDecimal royaltyPending,
                               BigDecimal lifetimeValue,
                               int openTicketCount) {
    }

    /**
     * @param liveBookCount   titles that reached "Published &amp; Live"
     * @param lifetimeValue  sum of MRP across live titles — a rough "value published" figure
     */
    public record AuthorDashboardResponse(String authorCode,
                                           String name,
                                           String email,
                                           String city,
                                           String phone,
                                           LocalDate joinedDate,
                                           AuthorStats stats,
                                           List<BookResponse> books,
                                           List<TicketDtos.TicketSummary> recentTickets) {
    }
}
