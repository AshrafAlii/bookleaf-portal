package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.Author;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorRepository extends JpaRepository<Author, UUID> {

    Optional<Author> findByUserId(UUID userId);

    Optional<Author> findByAuthorCode(String authorCode);

    List<Author> findAllByOrderByNameAsc();
}
