package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketStatus;
import com.bookleaf.portal.dto.BookDtos.AuthorDashboardResponse;
import com.bookleaf.portal.dto.BookDtos.AuthorStats;
import com.bookleaf.portal.dto.BookDtos.BookResponse;
import com.bookleaf.portal.dto.TicketDtos.TicketSummary;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.TicketRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The author portal's read model.
 */
@Service
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final TicketRepository ticketRepository;
    private final TicketService ticketService;

    public AuthorService(AuthorRepository authorRepository,
                         BookRepository bookRepository,
                         TicketRepository ticketRepository,
                         TicketService ticketService) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.ticketRepository = ticketRepository;
        this.ticketService = ticketService;
    }

    @Transactional(readOnly = true)
    public AuthorDashboardResponse dashboard(UUID authorId) {
        Author author = loadAuthor(authorId);
        List<Book> books = bookRepository.findAllForAuthor(authorId);
        List<TicketSummary> recentTickets = ticketService.listAuthorTickets(authorId).stream()
                .limit(5)
                .toList();

        int liveCount = (int) books.stream().filter(book -> !book.isInProduction()).count();
        int copiesSold = books.stream().mapToInt(Book::getTotalCopiesSold).sum();
        BigDecimal earned = sum(books, book -> book.getTotalRoyaltyEarned());
        BigDecimal paid = sum(books, book -> book.getRoyaltyPaid());
        BigDecimal pending = sum(books, book -> book.getRoyaltyPending());
        BigDecimal lifetimeValue = books.stream()
                .filter(book -> !book.isInProduction())
                .map(Book::getMrp)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long openTickets = ticketRepository.findAllForAuthor(authorId).stream()
                .filter(ticket -> !ticket.getStatus().isTerminal())
                .count();

        AuthorStats stats = new AuthorStats(
                books.size(),
                liveCount,
                books.size() - liveCount,
                copiesSold,
                earned,
                paid,
                pending,
                lifetimeValue,
                (int) openTickets);

        return new AuthorDashboardResponse(
                author.getAuthorCode(),
                author.getName(),
                author.getEmail(),
                author.getCity(),
                author.getPhone(),
                author.getJoinedDate(),
                stats,
                books.stream().map(BookResponse::from).toList(),
                recentTickets);
    }

    @Transactional(readOnly = true)
    public List<BookResponse> books(UUID authorId) {
        return bookRepository.findAllForAuthor(authorId).stream().map(BookResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookResponse book(UUID authorId, UUID bookId) {
        Book book = bookRepository.findByIdWithPlatforms(bookId)
                .filter(candidate -> candidate.getAuthor().getId().equals(authorId))
                .orElseThrow(() -> ApiException.notFound("Book not found"));
        return BookResponse.from(book);
    }

    private Author loadAuthor(UUID authorId) {
        return authorRepository.findById(authorId)
                .orElseThrow(() -> ApiException.notFound("Author profile not found"));
    }

    private BigDecimal sum(List<Book> books, java.util.function.Function<Book, BigDecimal> extractor) {
        return books.stream()
                .map(extractor)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
