package com.wifisense.repository;

import com.wifisense.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Exact name as stored: the subject of a session token. */
    Optional<User> findByUsername(String username);

    /**
     * Login: "Carlos" and "carlos" are the same account. Written as lower() = lower() so PostgreSQL uses the unique
     * index uq_users_username_lower (a derived IgnoreCase query uses upper() and would read the whole table).
     */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    /** Recovery and contact changes look the account up by email regardless of case (index uq_users_email_lower). */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    long countByRoleIn(Collection<User.Role> roles);

    List<User> findByRoleInOrderByUsername(Collection<User.Role> roles);
}
