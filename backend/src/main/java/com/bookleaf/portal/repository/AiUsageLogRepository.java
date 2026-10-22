package com.bookleaf.portal.repository;

import com.bookleaf.portal.domain.AiUsageLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("select l from AiUsageLog l order by l.createdAt desc")
    List<AiUsageLog> findRecent(int limit);

    @Query("""
            select coalesce(sum(l.promptTokens), 0) from AiUsageLog l where l.success = true and l.cached = false
            """)
    long sumPromptTokens();

    @Query("""
            select coalesce(sum(l.completionTokens), 0) from AiUsageLog l where l.success = true and l.cached = false
            """)
    long sumCompletionTokens();

    @Query("select count(l) from AiUsageLog l where l.cached = true")
    long countCacheHits();

    @Query("select count(l) from AiUsageLog l where l.success = false")
    long countFailures();

    @Query("select count(l) from AiUsageLog l")
    long countAll();
}
