package com.wifisense.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    /** ADMIN, ANALYST and VIEWER are the team; USER is a self-registered end user. */
    public enum Role {
        ADMIN, ANALYST, VIEWER, USER;

        public static final java.util.Set<Role> STAFF = java.util.EnumSet.of(ADMIN, ANALYST, VIEWER);

        public boolean isStaff() {
            return STAFF.contains(this);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Optional, international format (+573001234567); kept for recovery by SMS later. */
    @Column(length = 16)
    private String phone;

    /** Sessions issued before this moment are no longer accepted (see SessionTokenValidator). */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String username, String email, String passwordHash, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** Changes the password and closes every session opened before {@code at}. */
    public void changePassword(String newPasswordHash, Instant at) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = at;
    }

    public void changeContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public String getPhone() { return phone; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }

    /** False for the internal placeholder address of accounts registered without an email. */
    public boolean hasRealEmail() {
        return email != null && !email.endsWith(PLACEHOLDER_DOMAIN);
    }

    public static final String PLACEHOLDER_DOMAIN = "@wifisense.local";
    public Instant getCreatedAt() { return createdAt; }
}
