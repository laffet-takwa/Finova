package com.finova.user.repository;

import com.finova.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Issued refresh sessions, keyed by the JWT {@code jti}.
 * <p>
 * Revocation is a conditional {@code UPDATE} rather than a read followed by a
 * write, so two concurrent refreshes of the same token cannot both succeed: the
 * loser updates zero rows and is rejected. That closes the window in which a stolen
 * token could be redeemed twice in parallel.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenId(String tokenId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revoked = true
             where t.tokenId = :tokenId and t.revoked = false
            """)
    int revokeByTokenId(@Param("tokenId") String tokenId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
