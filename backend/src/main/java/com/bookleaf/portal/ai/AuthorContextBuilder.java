package com.bookleaf.portal.ai;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders live account data as compact, factual context for the prompts.
 *
 * <p>This is what lets the model write "Your royalty of ₹3,570 is pending and
 * will be paid in the next quarterly cycle" instead of a vague reassurance —
 * the numbers come from the database, never from the model's imagination.</p>
 */
@Component
public class AuthorContextBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String IND = "₹";

    public String build(Author author, List<Book> books) {
        StringBuilder sb = new StringBuilder();
        sb.append("Author: ").append(author.getName())
                .append(" (").append(author.getAuthorCode()).append(")")
                .append(", email ").append(author.getEmail())
                .append(", city ").append(author.getCity())
                .append(", joined ").append(author.getJoinedDate().format(DATE))
                .append(".\n");

        if (books == null || books.isEmpty()) {
            sb.append("Books: none on this account.\n");
            return sb.toString();
        }

        sb.append("Books:\n");
        for (Book book : books) {
            sb.append("- ").append(book.getTitle())
                    .append(" (").append(book.getBookCode()).append(")")
                    .append(", ISBN ").append(book.getIsbn())
                    .append(", genre ").append(book.getGenre())
                    .append(", status ").append(book.getStatus())
                    .append(", published ")
                    .append(book.getPublicationDate() == null ? "not yet" : book.getPublicationDate().format(DATE))
                    .append(", MRP ").append(money(book.getMrp()))
                    .append(", royalty per copy ").append(money(book.getAuthorRoyaltyPerCopy()))
                    .append(", copies sold ").append(book.getTotalCopiesSold())
                    .append(", royalty earned ").append(money(book.getTotalRoyaltyEarned()))
                    .append(", royalty paid ").append(money(book.getRoyaltyPaid()))
                    .append(", royalty pending ").append(money(book.getRoyaltyPending()))
                    .append(", last payout ").append(book.getLastRoyaltyPayoutDate() == null
                            ? "none yet" : book.getLastRoyaltyPayoutDate().format(DATE))
                    .append(", print partner ").append(book.getPrintPartner() == null ? "not assigned" : book.getPrintPartner())
                    .append(", platforms: ")
                    .append(book.getAvailableOn().isEmpty()
                            ? "none listed yet"
                            : String.join(", ", book.getAvailableOn()))
                    .append(".\n");
        }
        return sb.toString();
    }

    /** Compact one-line summary used in ticket list rows. */
    public String summariseBooks(List<Book> books) {
        if (books == null || books.isEmpty()) {
            return "No books on this account";
        }
        return books.stream()
                .map(book -> book.getBookCode() + " " + book.getTitle())
                .collect(Collectors.joining("; "));
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "not set";
        }
        return IND + String.format("%,.2f", value);
    }
}
