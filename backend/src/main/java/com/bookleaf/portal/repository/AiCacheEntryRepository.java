package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.AiCacheEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCacheEntryRepository extends JpaRepository<AiCacheEntry, Long> {

    Optional<AiCacheEntry> findByCacheKey(String cacheKey);
}
