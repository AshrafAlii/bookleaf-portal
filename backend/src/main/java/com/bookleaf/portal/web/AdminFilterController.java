package com.bookleaf.portal.web;

import com.bookleaf.portal.dto.AdminDtos.SavedFilterRequest;
import com.bookleaf.portal.dto.AdminDtos.SavedFilterResponse;
import com.bookleaf.portal.security.AppUserPrincipal;
import com.bookleaf.portal.service.SavedFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved queue filters.
 *
 * <p>Filters are private to the admin who created them — an ops lead can keep
 * their own view of the queue without imposing it on the rest of the team.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/filters")
@Tag(name = "Admin saved filters", description = "Named queue filters for repeat work")
@SecurityRequirement(name = "bearerAuth")
public class AdminFilterController {

    private final SavedFilterService savedFilterService;

    public AdminFilterController(SavedFilterService savedFilterService) {
        this.savedFilterService = savedFilterService;
    }

    @GetMapping
    @Operation(summary = "List the signed-in admin's saved filters")
    public List<SavedFilterResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return savedFilterService.list(principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save the current queue filters under a name")
    public SavedFilterResponse create(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @Valid @RequestBody SavedFilterRequest request) {
        return savedFilterService.create(principal.getUserId(), request.name(), request.filters());
    }

    @DeleteMapping("/{filterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a saved filter")
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID filterId) {
        savedFilterService.delete(principal.getUserId(), filterId);
    }
}
