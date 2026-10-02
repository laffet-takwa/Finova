package com.finova.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only view of the user-service needed by the dev data seeder.
 * <p>
 * Demo users are created by the user-service with random ids, so the seeder has
 * to resolve them by email over the network instead of hard-coding ids.
 */
@FeignClient(name = "user-service", url = "${finova.clients.user-service-url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/users/by-email")
    UserReference findByEmail(@RequestParam("email") String email);
}
