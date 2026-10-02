package com.finova.user.service;

import com.finova.user.domain.AuditLog;
import com.finova.user.dto.AuditLogResponse;
import com.finova.user.mapper.UserMapper;
import com.finova.user.repository.AuditLogRepository;
import com.finova.user.repository.AuditSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Read side of the platform audit trail.
 * <p>
 * Every row is returned to an administrator, including rows this service recorded
 * for its own sign-ins and rows it consumed from the other services, because the
 * value of an audit trail is the single timeline rather than four per-service logs.
 */
@Service
public class AuditTrailService {

    private final AuditLogRepository auditLogRepository;
    private final UserMapper userMapper;

    public AuditTrailService(AuditLogRepository auditLogRepository, UserMapper userMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String userId, String action, String result, String search,
                                         Instant from, Instant to, int page, int size) {
        int effectiveSize = size <= 0 ? 20 : Math.min(size, 200);
        Page<AuditLog> rows = auditLogRepository.findAll(
                AuditSpecifications.filter(userId, action, result, search, from, to),
                PageRequest.of(Math.max(page, 0), effectiveSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return rows.map(userMapper::toAuditLogResponse);
    }
}
