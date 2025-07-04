package com.fitech.app.users.infrastructure.repository;

import com.fitech.app.users.domain.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsernameAndPassword(String username, String password);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByEmailVerificationToken(String token);
    boolean existsByPersonEmail(String email);
}