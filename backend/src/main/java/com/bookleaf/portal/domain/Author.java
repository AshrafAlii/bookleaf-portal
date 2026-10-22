package com.bookleaf.portal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * The publishing partner (author) behind a {@link User} login.
 */
@BatchSize(size = 50)
@Entity
@Table(name = "author")
public class Author {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** Human-readable code from the source dataset, e.g. {@code AUTH001}. */
    @Column(name = "author_code", nullable = false, unique = true, length = 20)
    private String authorCode;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    private String city;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    protected Author() {
    }

    public Author(String authorCode, User user, String name, String email, String phone, String city,
                  LocalDate joinedDate) {
        this.authorCode = authorCode;
        this.user = user;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.city = city;
        this.joinedDate = joinedDate;
    }

    public UUID getId() {
        return id;
    }

    public String getAuthorCode() {
        return authorCode;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getCity() {
        return city;
    }

    public LocalDate getJoinedDate() {
        return joinedDate;
    }
}
