package com.bookleaf.portal.support;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.Book;
import com.bookleaf.portal.domain.Role;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.BookRepository;
import com.bookleaf.portal.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test fixtures: authors, admins and books created on demand so assertions can
 * be exact instead of depending on the seeded demo data.
 */
@TestConfiguration
public class TestFixtures {

    @Bean
    public FixtureFactory fixtureFactory(UserRepository userRepository,
                                          AuthorRepository authorRepository,
                                          BookRepository bookRepository,
                                          PasswordEncoder passwordEncoder) {
        return new FixtureFactory(userRepository, authorRepository, bookRepository, passwordEncoder);
    }

    public static class FixtureFactory {

        private final UserRepository userRepository;
        private final AuthorRepository authorRepository;
        private final BookRepository bookRepository;
        private final PasswordEncoder passwordEncoder;

        FixtureFactory(UserRepository userRepository,
                       AuthorRepository authorRepository,
                       BookRepository bookRepository,
                       PasswordEncoder passwordEncoder) {
            this.userRepository = userRepository;
            this.authorRepository = authorRepository;
            this.bookRepository = bookRepository;
            this.passwordEncoder = passwordEncoder;
        }

        public User createAdmin(String email, String name, String password) {
            return userRepository.save(new User(email, passwordEncoder.encode(password), name, Role.ADMIN));
        }

        public Author createAuthor(String code, String name, String email, String password) {
            User user = userRepository.save(
                    new User(email, passwordEncoder.encode(password), name, Role.AUTHOR));
            return authorRepository.save(new Author(code, user, name, email, "+91-00000-00000",
                    "Mumbai", LocalDate.of(2023, 1, 15)));
        }

        public Book createBook(Author author, String code, String title, String status, BigDecimal mrp) {
            Book book = new Book(author, code, title, "978-93-5XXXX-99-9");
            book.setGenre("Literary Fiction");
            book.setStatus(status);
            book.setMrp(mrp);
            book.setAuthorRoyaltyPerCopy(new BigDecimal("35"));
            book.setTotalCopiesSold(342);
            book.setTotalRoyaltyEarned(new BigDecimal("11970"));
            book.setRoyaltyPaid(new BigDecimal("8400"));
            book.setRoyaltyPending(new BigDecimal("3570"));
            book.setLastRoyaltyPayoutDate(LocalDate.of(2025, 10, 15));
            book.setPrintPartner("In-House");
            book.getAvailableOn().add("Amazon India");
            book.getAvailableOn().add("BookLeaf Store");
            return bookRepository.save(book);
        }

        public UUID idOf(Object entity) {
            if (entity instanceof Author author) {
                return author.getId();
            }
            if (entity instanceof Book book) {
                return book.getId();
            }
            if (entity instanceof User user) {
                return user.getId();
            }
            throw new IllegalArgumentException("Unsupported entity " + entity);
        }

        public List<Author> allAuthors() {
            return authorRepository.findAll();
        }

        public Book bookByCode(String code) {
            return bookRepository.findByBookCode(code)
                    .orElseThrow(() -> new IllegalStateException("No book " + code));
        }
    }
}
