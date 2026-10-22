package com.bookleaf.portal.security;

import com.bookleaf.portal.domain.Role;
import com.bookleaf.portal.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal. Carries the user id and (for authors) the author id
 * so authorisation checks never need a second database round trip.
 */
public class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID authorId;
    private final String email;
    private final String fullName;
    private final Role role;
    private final boolean active;

    public AppUserPrincipal(User user, UUID authorId) {
        this.userId = user.getId();
        this.authorId = authorId;
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.active = user.isActive();
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
