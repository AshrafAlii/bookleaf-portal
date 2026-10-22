package com.bookleaf.portal.service;

import com.bookleaf.portal.domain.SavedFilter;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.dto.AdminDtos.SavedFilterResponse;
import com.bookleaf.portal.exception.ApiException;
import com.bookleaf.portal.repository.SavedFilterRepository;
import com.bookleaf.portal.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved queue filters.
 *
 * <p>An admin can store the filters they use every day — "Overdue royalties",
 * "My Critical tickets", "Unassigned distribution issues" — and re-apply them
 * with one click instead of re-selecting six dropdowns each time.</p>
 *
 * <p>Filters are private to the admin who saved them.</p>
 */
@Service
public class SavedFilterService {

    private final SavedFilterRepository savedFilterRepository;
    private final UserRepository userRepository;

    public SavedFilterService(SavedFilterRepository savedFilterRepository, UserRepository userRepository) {
        this.savedFilterRepository = savedFilterRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SavedFilterResponse> list(UUID ownerId) {
        return savedFilterRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedFilterResponse create(UUID ownerId, String name, String filtersJson) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (savedFilterRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, name.strip())) {
            throw ApiException.conflict("You already have a saved filter called '" + name.strip() + "'");
        }

        SavedFilter saved = savedFilterRepository.save(new SavedFilter(owner, name.strip(), filtersJson.strip()));
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID ownerId, UUID filterId) {
        SavedFilter filter = savedFilterRepository.findByIdAndOwnerId(filterId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Saved filter not found"));
        savedFilterRepository.delete(filter);
    }

    private SavedFilterResponse toResponse(SavedFilter filter) {
        return new SavedFilterResponse(filter.getId(), filter.getName(), filter.getFilters(),
                filter.getCreatedAt());
    }
}
