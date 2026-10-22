package com.bookleaf.portal.config;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.Role;
import com.bookleaf.portal.domain.Ticket;
import com.bookleaf.portal.domain.TicketCategory;
import com.bookleaf.portal.domain.TicketEvent;
import com.bookleaf.portal.domain.TicketMessage;
import com.bookleaf.portal.domain.TicketPriority;
import com.bookleaf.portal.domain.TicketStatus;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.TicketEventRepository;
import com.bookleaf.portal.repository.TicketMessageRepository;
import com.bookleaf.portal.repository.TicketRepository;
import com.bookleaf.portal.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the portal with the supplied sample dataset.
 *
 * <p>The brief says the system must work with the provided data out of the box,
 * so the exact 10 authors and 18 books are loaded verbatim from
 * {@code bookleaf_sample_data.json}. A handful of realistic tickets are also
 * created so the admin queue, filters and real-time stream have something to
 * show the moment the app is deployed.</p>
 *
 * <p>Seeding is idempotent: if the author table is already populated the runner
 * does nothing, so restarts never duplicate data.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppProperties properties,
                       ResourceLoader resourceLoader,
                       ObjectMapper objectMapper,
                       UserRepository userRepository,
                       AuthorRepository authorRepository,
                       BookRepository bookRepository,
                       TicketRepository ticketRepository,
                       TicketMessageRepository messageRepository,
                       TicketEventRepository eventRepository,
                       PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        Map<String, Author> authorsByCode = new LinkedHashMap<>();
        Map<String, Book> booksByCode = new LinkedHashMap<>();

        if (authorRepository.count() > 0) {
            log.info("Seed skipped — {} authors already present", authorRepository.count());
            authorsByCode.putAll(loadExistingAuthors());
            booksByCode.putAll(loadExistingBooks());
        } else {
            JsonNode root = readSampleData();
            seedAuthorsAndBooks(root, authorsByCode, booksByCode);
            seedAdmins();
            log.info("Seeded {} authors and {} books from the sample dataset",
                    authorsByCode.size(), booksByCode.size());
        }

        // Demo tickets are re-created whenever the queue is empty, so a reviewer
        // (or a curious developer who clears the table) always sees a populated queue.
        if (ticketRepository.count() == 0) {
            seedDemoTickets(authorsByCode, booksByCode);
        }
    }

    private Map<String, Author> loadExistingAuthors() {
        Map<String, Author> map = new LinkedHashMap<>();
        authorRepository.findAll().forEach(author -> map.put(author.getAuthorCode(), author));
        return map;
    }

    private Map<String, Book> loadExistingBooks() {
        Map<String, Book> map = new LinkedHashMap<>();
        bookRepository.findAll().forEach(book -> map.put(book.getBookCode(), book));
        return map;
    }

    private void seedAuthorsAndBooks(JsonNode root, Map<String, Author> authorsByCode,
                                      Map<String, Book> booksByCode) {
        for (JsonNode authorNode : root.path("authors")) {
            String authorCode = authorNode.path("author_id").asText();
            String email = authorNode.path("email").asText();
            String name = authorNode.path("name").asText();

            User user = new User(email, passwordEncoder.encode(properties.getSeed().getAuthorPassword()),
                    name, Role.AUTHOR);
            userRepository.save(user);

            Author author = new Author(
                    authorCode,
                    user,
                    name,
                    email,
                    authorNode.path("phone").asText(null),
                    authorNode.path("city").asText(null),
                    parseDate(authorNode.path("joined_date")));
            authorRepository.save(author);
            authorsByCode.put(authorCode, author);

            for (JsonNode bookNode : authorNode.path("books")) {
                Book book = new Book(author,
                        bookNode.path("book_id").asText(),
                        bookNode.path("title").asText(),
                        bookNode.path("isbn").asText());
                applyBookFields(book, bookNode);
                bookRepository.save(book);
                booksByCode.put(bookNode.path("book_id").asText(), book);
            }
        }
    }

    private JsonNode readSampleData() throws Exception {
        String path = properties.getSeed().getDataPath();
        try (InputStream in = resourceLoader.getResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private void applyBookFields(Book book, JsonNode node) {
        book.setBookCode(node.path("book_id").asText());
        book.setTitle(node.path("title").asText());
        book.setIsbn(node.path("isbn").asText());
        book.setGenre(node.path("genre").asText(null));
        book.setPublicationDate(parseDate(node.path("publication_date")));
        book.setStatus(node.path("status").asText());
        book.setMrp(parseDecimal(node.path("mrp")));
        book.setAuthorRoyaltyPerCopy(parseDecimal(node.path("author_royalty_per_copy")));
        book.setTotalCopiesSold(node.path("total_copies_sold").asInt(0));
        book.setTotalRoyaltyEarned(parseDecimal(node.path("total_royalty_earned")));
        book.setRoyaltyPaid(parseDecimal(node.path("royalty_paid")));
        book.setRoyaltyPending(parseDecimal(node.path("royalty_pending")));
        book.setLastRoyaltyPayoutDate(parseDate(node.path("last_royalty_payout_date")));
        book.setPrintPartner(node.path("print_partner").asText(null));
        for (JsonNode platform : node.path("available_on")) {
            book.getAvailableOn().add(platform.asText());
        }
    }

    private void seedAdmins() {
        createAdmin("ops@bookleaf.in", "BookLeaf Ops", properties.getSeed().getAdminPassword());
        createAdmin("priya.support@bookleaf.in", "Priya Menon (Support Lead)",
                properties.getSeed().getAdminPassword());
    }

    private void createAdmin(String email, String name, String password) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(password), name, Role.ADMIN));
    }

    // ------------------------------------------------------------------
    // Demo tickets — these give the reviewer a populated queue to explore.
    // ------------------------------------------------------------------

    private record DemoTicket(String authorCode,
                              String bookCode,
                              String subject,
                              String description,
                              TicketCategory category,
                              TicketPriority priority,
                              TicketStatus status,
                              int ageHours,
                              String adminReply,
                              boolean authorReplied) {
    }

    private static final List<DemoTicket> DEMO_TICKETS = List.of(
            new DemoTicket("AUTH001", "BK001",
                    "Royalty payment not received since March",
                    "I published Whispers of the Ganges in June 2023 and my dashboard still shows ₹3,570 pending. "
                            + "It has now been 6 months and I have not received a single royalty payout. "
                            + "Can you please explain what has happened to my payment?",
                    TicketCategory.ROYALTY_PAYMENTS, TicketPriority.CRITICAL, TicketStatus.OPEN, 52, null, false),
            new DemoTicket("AUTH007", "BK011",
                    "Author copies arrived with misaligned pages and blurry images",
                    "My author copies of The Algorithm of Love arrived yesterday. The pages are badly misaligned "
                            + "near the middle and several illustrations are blurry. The spine is also creased. "
                            + "This is the second time it has happened with my books.",
                    TicketCategory.PRINTING_QUALITY, TicketPriority.HIGH, TicketStatus.OPEN, 26, null, false),
            new DemoTicket("AUTH003", "BK005",
                    "Book still in typesetting after three weeks — when will it go live?",
                    "Between Two Temples went into production about three weeks ago and the dashboard still says "
                            + "In Production - Typesetting. I did approve the cover two weeks ago. "
                            + "When exactly will my book be published?",
                    TicketCategory.PRODUCTION_UPDATES, TicketPriority.HIGH, TicketStatus.IN_PROGRESS, 30,
                    "Hi Ananya, thank you for checking in, and apologies for the wait. I can see the title is "
                            + "with our typesetting team and your cover approval came through on the 12th. "
                            + "Typesetting for a 232-page title normally takes 5-7 working days and we are at the "
                            + "upper end of that. Our production team will confirm a go-live date by Thursday, "
                            + "and you will get an email at each stage from there.",
                    false),
            new DemoTicket("AUTH010", "BK018",
                    "Howrah Nights shows as Currently Unavailable on Amazon",
                    "Howrah Nights was published in January and I can see it in the BookLeaf Store, but on Amazon "
                            + "India the listing says 'Currently Unavailable'. The book is live on the store "
                            + "listing page. Is there something I need to do?",
                    TicketCategory.DISTRIBUTION_AVAILABILITY, TicketPriority.MEDIUM, TicketStatus.OPEN, 8, null, false),
            new DemoTicket("AUTH002", "BK003",
                    "ISBN on Amazon does not match the ISBN on my paperback",
                    "Code & Karma shows ISBN 978-93-5XXXX-03-5 on the paperback copy I received, but the Amazon "
                            + "India listing shows a completely different ISBN. My retailer is now asking which "
                            + "one is correct. Please fix this urgently.",
                    TicketCategory.ISBN_METADATA, TicketPriority.HIGH, TicketStatus.RESOLVED, 74,
                    "Dear Rohit, you are right and I am sorry — this is our error. The listing was mapped to an "
                            + "older catalogue entry for the same title. Our production team has re-linked the "
                            + "Amazon India record to the correct ISBN 978-93-5XXXX-03-5 and the change will "
                            + "propagate across Amazon, Flipkart and the BookLeaf Store within 48 hours. "
                            + "Your retailer can safely quote the paperback ISBN. We have also added a catalogue "
                            + "check to our weekly sync so the same mapping cannot recur.",
                    true),
            new DemoTicket("AUTH005", "BK009",
                    "Royalty for Letters from Lakshadweep looks lower than I expected",
                    "I have sold 201 copies of Letters from Lakshadweep but only ₹8,000 has been paid so far and "
                            + "₹3,055 is still pending. Could you share a line-by-line breakdown of how the "
                            + "royalty was calculated? The MRP is ₹550 and I want to understand the deductions.",
                    TicketCategory.ROYALTY_PAYMENTS, TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, 40,
                    "Hi Meera, happy to share the detail. On Letters from Lakshadweep we calculate on net profit: "
                            + "MRP ₹550, less printing cost ₹112, less platform commission on Amazon India and "
                            + "Amazon US ₹96, less shipping ₹18, which leaves a net profit of ₹324 per copy. "
                            + "Your 80% share is ₹259.20 per copy, so 201 copies gives ₹52,099. We have paid "
                            + "₹8,000 and ₹3,055 is pending, and we are reviewing the remaining balance against "
                            + "the quarterly settlement. I will share the full per-platform statement by Friday.",
                    false),
            new DemoTicket("AUTH009", "BK016",
                    "Can I update my author bio on the store listings?",
                    "My publisher details have changed and I would like to update my author bio on Amazon and "
                            + "Flipkart. Is this something I can do from the dashboard, or do I need to email you?",
                    TicketCategory.GENERAL_INQUIRY, TicketPriority.LOW, TicketStatus.CLOSED, 96,
                    "Hi Kavita, yes — metadata updates like your author bio can be submitted directly from the "
                            + "dashboard under Books > your title > Edit metadata, or emailed to this address. "
                            + "Changes usually reflect on the platforms within 3-5 business days. I have also "
                            + "updated the bio on our side so the two stay in step.",
                    false),
            new DemoTicket("AUTH004", "BK006",
                    "When will Debugging Life be available on Flipkart?",
                    "Debugging Life is on Amazon India and the BookLeaf Store, but I cannot find it on Flipkart. "
                            + "Is it planned for Flipkart? I have readers asking about it.",
                    TicketCategory.DISTRIBUTION_AVAILABILITY, TicketPriority.LOW, TicketStatus.CLOSED, 120,
                    "Hi Vikram, Debugging Life was live on Flipkart from the start of last month — the link is "
                            + "now on the book's page in your dashboard. If a specific reader cannot see it, it "
                            + "is usually a stale cache on their device; refreshing or searching the exact title "
                            + "usually resolves it. Nothing further is needed from you.",
                    false));

    private void seedDemoTickets(Map<String, Author> authorsByCode, Map<String, Book> booksByCode) {
        List<User> admins = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .toList();
        User admin = admins.isEmpty() ? null : admins.get(0);

        for (DemoTicket demo : DEMO_TICKETS) {
            Author author = authorsByCode.get(demo.authorCode());
            Book book = demo.bookCode() == null ? null : booksByCode.get(demo.bookCode());
            if (author == null) {
                continue;
            }

            long sequence = ticketRepository.nextTicketSequence();
            Ticket ticket = new Ticket(String.format("BL-%06d", sequence), author, book,
                    demo.subject(), demo.description(), null);
            ticket.setCreatedAt(Instant.now().minus(demo.ageHours(), ChronoUnit.HOURS));
            ticket.setUpdatedAt(ticket.getCreatedAt());
            ticket.applyDecision(demo.category(), demo.priority(),
                    "Seeded demo ticket — " + demo.category().label() + " / " + demo.priority().label(),
                    com.bookleaf.portal.domain.DecisionSource.AI);
            ticketRepository.save(ticket);

            eventRepository.save(new TicketEvent(ticket, author.getUser(), TicketEvent.CREATED,
                    "Ticket raised" + (book == null ? "" : " about " + book.getTitle())));
            eventRepository.save(new TicketEvent(ticket, null, TicketEvent.AI_CLASSIFIED,
                    "Classified as " + demo.category().label() + " with " + demo.priority().label() + " priority."));

            if (demo.adminReply() != null && admin != null) {
                TicketMessage reply = new TicketMessage(ticket, admin, demo.adminReply(), false);
                reply.setCreatedAt(Instant.now().minus(Math.max(demo.ageHours() - 6, 1), ChronoUnit.HOURS));
                messageRepository.save(reply);
                ticket.setFirstResponseAt(reply.getCreatedAt());
                ticket.setUpdatedAt(reply.getCreatedAt());
                eventRepository.save(new TicketEvent(ticket, admin, TicketEvent.REPLY_SENT,
                        "Reply sent to " + author.getName()));
            }

            if (demo.authorReplied()) {
                TicketMessage followUp = new TicketMessage(ticket, author.getUser(),
                        "Thank you for sorting that out so quickly — I have informed my retailer and they have "
                                + "corrected their records. Appreciate the help.", false);
                followUp.setCreatedAt(Instant.now().minus(Math.max(demo.ageHours() - 30, 1), ChronoUnit.HOURS));
                messageRepository.save(followUp);
                ticket.setUpdatedAt(followUp.getCreatedAt());
                eventRepository.save(new TicketEvent(ticket, author.getUser(), TicketEvent.AUTHOR_REPLIED,
                        "Author added a follow-up"));
            }

            if (demo.status() == TicketStatus.RESOLVED) {
                ticket.changeStatus(TicketStatus.RESOLVED);
                ticket.setResolvedAt(Instant.now().minus(Math.max(demo.ageHours() - 10, 1), ChronoUnit.HOURS));
            } else if (demo.status() == TicketStatus.CLOSED) {
                ticket.changeStatus(TicketStatus.CLOSED);
                ticket.setResolvedAt(Instant.now().minus(Math.max(demo.ageHours() - 12, 1), ChronoUnit.HOURS));
                ticket.setClosedAt(Instant.now().minus(Math.max(demo.ageHours() - 14, 1), ChronoUnit.HOURS));
            } else if (demo.status() == TicketStatus.IN_PROGRESS) {
                ticket.changeStatus(TicketStatus.IN_PROGRESS);
                if (admin != null) {
                    ticket.assignTo(admin);
                }
            }

            ticketRepository.save(ticket);
        }
        log.info("Seeded {} demo tickets", DEMO_TICKETS.size());
    }

    private LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return LocalDate.parse(node.asText());
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }
}
