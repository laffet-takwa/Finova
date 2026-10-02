package com.finova.user;

import com.finova.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * User service: identity, authentication, profile and the platform audit trail.
 * <p>
 * It is the only issuer of Finova JWTs, so the api-gateway and every downstream
 * service verify a token minted here with the same secret. It is also the single
 * writer of the audit trail, fed by {@code audit.recorded} from the other services.
 * <p>
 * JPA auditing is enabled by {@code JpaAuditingConfig} rather than here, so a web
 * slice test can start without a persistence context.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
