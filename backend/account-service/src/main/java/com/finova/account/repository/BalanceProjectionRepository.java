package com.finova.account.repository;

import com.finova.account.domain.BalanceProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceProjectionRepository extends JpaRepository<BalanceProjection, String> {
}
