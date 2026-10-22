package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.SavedFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {

    List<SavedFilter> findByOwnerIdOrderByNameAsc(UUID ownerId);

    Optional<SavedFilter> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);
}
