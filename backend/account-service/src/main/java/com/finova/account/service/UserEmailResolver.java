package com.finova.account.service;

import com.finova.account.client.UserReference;
import com.finova.account.client.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves an owner email to a user id through the user-service.
 * <p>
 * The account table stores only {@code user_id}, so filtering accounts by email
 * needs the owner directory. The lookup is best effort by design: when the
 * user-service is unreachable the filter simply matches nothing instead of
 * failing a read that would otherwise succeed.
 */
@Component
public class UserEmailResolver {

    private static final Logger log = LoggerFactory.getLogger(UserEmailResolver.class);

    private final UserServiceClient userServiceClient;

    public UserEmailResolver(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    public Optional<String> resolveUserId(String email) {
        try {
            UserReference user = userServiceClient.findByEmail(email);
            if (user == null || user.id() == null || user.id().isBlank()) {
                log.warn("user-service returned no user for {}", email);
                return Optional.empty();
            }
            return Optional.of(user.id());
        } catch (Exception ex) {
            log.warn("Could not resolve {} from the user-service ({}); the email filter matches nothing",
                email, ex.getMessage());
            return Optional.empty();
        }
    }
}