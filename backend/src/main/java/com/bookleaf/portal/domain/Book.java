package com.bookleaf.portal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * A title published for an author, including the royalty ledger.
 *
 * <p>Books that are still in production legitimately carry {@code null} for MRP,
 * royalty rate and publication date — the API models those as absent rather than 0
 * so the UI can say "not applicable yet" instead of showing a fake price.</p>
 */
@BatchSize(size = 50)
@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Human-readable code from the source dataset, e.g. {@code BK001}. */
    @Column(name = "book_code", nullable = false, unique = true, length = 20)
    private String bookCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 40)
    private String isbn;

    private String genre;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(nullable = false, length = 60)
    private String status;

    @Column(precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(name = "author_royalty_per_copy", precision = 10, scale = 2)
    private BigDecimal authorRoyaltyPerCopy;

    @Column(name = "total_copies_sold", nullable = false)
    private int totalCopiesSold;

    @Column(name = "total_royalty_earned", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalRoyaltyEarned = BigDecimal.ZERO;

    @Column(name = "royalty_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyPaid = BigDecimal.ZERO;

    @Column(name = "royalty_pending", nullable = false, precision = 14, scale = 2)
    private BigDecimal royaltyPending = BigDecimal.ZERO;

    @Column(name = "last_royalty_payout_date")
    private LocalDate lastRoyaltyPayoutDate;

    @Column(name = "print_partner", length = 120)
    private String printPartner;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "book_platform", joinColumns = @JoinColumn(name = "book_id"))
    @Column(name = "platform", nullable = false, length = 80)
    private Set<String> availableOn = new LinkedHashSet<>();

    protected Book() {
    }

    public Book(Author author, String bookCode, String title, String isbn) {
        this.author = author;
        this.bookCode = bookCode;
        this.title = title;
        this.isbn = isbn;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public void setBookCode(String bookCode) {
        this.bookCode = bookCode;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public void setPublicationDate(LocalDate publicationDate) {
        this.publicationDate = publicationDate;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setMrp(BigDecimal mrp) {
        this.mrp = mrp;
    }

    public void setAuthorRoyaltyPerCopy(BigDecimal authorRoyaltyPerCopy) {
        this.authorRoyaltyPerCopy = authorRoyaltyPerCopy;
    }

    public void setTotalCopiesSold(int totalCopiesSold) {
        this.totalCopiesSold = totalCopiesSold;
    }

    public void setTotalRoyaltyEarned(BigDecimal totalRoyaltyEarned) {
        this.totalRoyaltyEarned = totalRoyaltyEarned;
    }

    public void setRoyaltyPaid(BigDecimal royaltyPaid) {
        this.royaltyPaid = royaltyPaid;
    }

    public void setRoyaltyPending(BigDecimal royaltyPending) {
        this.royaltyPending = royaltyPending;
    }

    public void setLastRoyaltyPayoutDate(LocalDate lastRoyaltyPayoutDate) {
        this.lastRoyaltyPayoutDate = lastRoyaltyPayoutDate;
    }

    public void setPrintPartner(String printPartner) {
        this.printPartner = printPartner;
    }

    public UUID getId() {
        return id;
    }

    public String getBookCode() {
        return bookCode;
    }

    public Author getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getGenre() {
        return genre;
    }

    public LocalDate getPublicationDate() {
        return publicationDate;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public BigDecimal getAuthorRoyaltyPerCopy() {
        return authorRoyaltyPerCopy;
    }

    public int getTotalCopiesSold() {
        return totalCopiesSold;
    }

    public BigDecimal getTotalRoyaltyEarned() {
        return totalRoyaltyEarned;
    }

    public BigDecimal getRoyaltyPaid() {
        return royaltyPaid;
    }

    public BigDecimal getRoyaltyPending() {
        return royaltyPending;
    }

    public LocalDate getLastRoyaltyPayoutDate() {
        return lastRoyaltyPayoutDate;
    }

    public String getPrintPartner() {
        return printPartner;
    }

    public Set<String> getAvailableOn() {
        return availableOn;
    }

    /** True when the title has not reached the "Published &amp; Live" stage yet. */
    public boolean isInProduction() {
        return !"Published & Live".equalsIgnoreCase(status);
    }
}
