package com.bookleaf.portal.security;

import com.bookleaf.portal.domain.Author;
import com.bookleaf.portal.domain.User;
import com.bookleaf.portal.repository.AuthorRepository;
import com.bookleaf.portal.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the authenticated principal for a user id. The author id is resolved in
 * the same call so downstream authorisation checks are free.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AuthorRepository authorRepository;

    public AppUserDetailsService(UserRepository userRepository, AuthorRepository authorRepository) {
        this.userRepository = userRepository;
        this.authorRepository = authorRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> loadPrincipal(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(user -> new AppUserPrincipal(user, resolveAuthorId(user)));
    }

    @Override
    public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(username)
                .filter(User::isActive)
                .map(user -> new AppUserPrincipal(user, resolveAuthorId(user)))
                .orElseThrow(() -> new UsernameNotFoundException("No active user for " + username));
    }

    private UUID resolveAuthorId(User user) {
        if (user.getRole() != com.bookleaf.portal.domain.Role.AUTHOR) {
            return null;
        }
        return authorRepository.findByUserId(user.getId())
                .map(Author::getId)
                .orElse(null);
    }
}
