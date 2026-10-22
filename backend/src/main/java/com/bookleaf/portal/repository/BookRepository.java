package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.Book;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, UUID> {

    Optional<Book> findByBookCode(String bookCode);

    /**
     * Eagerly fetches the platform collection — the author book list is rendered in one shot.
     */
    @Query("select distinct b from Book b left join fetch b.availableOn where b.author.id = :authorId "
            + "order by b.publicationDate desc nulls last, b.title asc")
    List<Book> findAllForAuthor(@Param("authorId") UUID authorId);

    @Query("select distinct b from Book b left join fetch b.availableOn where b.id = :id")
    Optional<Book> findByIdWithPlatforms(@Param("id") UUID id);

    @Query("select distinct b from Book b left join fetch b.availableOn where b.bookCode = :code")
    Optional<Book> findByBookCodeWithPlatforms(@Param("code") String code);
}
