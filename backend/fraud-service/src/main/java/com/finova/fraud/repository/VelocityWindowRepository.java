package com.finova.fraud.repository;

import com.finova.fraud.domain.VelocityWindow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface VelocityWindowRepository extends MongoRepository<VelocityWindow, String> {

    List<VelocityWindow> findBySenderAccountId(String senderAccountId);

    long countByUpdatedAtLessThan(Instant cutoff);

    long deleteByUpdatedAtBefore(Instant cutoff);
}
