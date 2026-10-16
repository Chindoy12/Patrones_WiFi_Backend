package com.wifisense.repository;

import com.wifisense.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Recovery and contact changes look the account up by email regardless of case (index idx_users_email_lower). */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    long countByRoleIn(Collection<User.Role> roles);

    List<User> findByRoleInOrderByUsername(Collection<User.Role> roles);
}
