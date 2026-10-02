package com.finova.user.repository;

import com.finova.common.domain.Role;
import com.finova.common.domain.UserStatus;
import com.finova.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Identity lookups.
 * <p>
 * Every method that can reach a row outside the caller's own account is reached only
 * from a path already gated on ADMIN; the repository itself never guesses who is
 * calling. Email lookups assume the address has already been normalised the same
 * way registration normalised it, which is the only reason the equality match is
 * exhaustive.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    long countByRole(Role role);

    long countByCreatedAtAfter(Instant since);

    @Query("select u.createdAt from User u")
    List<Instant> findAllCreatedAt();
}
