package com.wifisense.repository;

import com.wifisense.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleIn(Collection<User.Role> roles);

    List<User> findByRoleInOrderByUsername(Collection<User.Role> roles);
}
