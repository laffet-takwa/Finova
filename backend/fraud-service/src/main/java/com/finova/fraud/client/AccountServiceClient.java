package com.finova.fraud.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Contract coded against the account service.
 * <p>
 * {@code PUT /api/accounts/{id}/status} with {@code {"status":"BLOCKED","reason":"..."}} returning
 * the updated account. The account service is idempotent on this call, so re-blocking an already
 * blocked account is a success rather than an error.
 */
@FeignClient(name = "account-service", path = "/api/accounts")
public interface AccountServiceClient {

    @PutMapping("/{id}/status")
    Map<String, Object> updateStatus(@PathVariable("id") String accountId,
                                     @RequestBody AccountStatusUpdateRequest request);
}
