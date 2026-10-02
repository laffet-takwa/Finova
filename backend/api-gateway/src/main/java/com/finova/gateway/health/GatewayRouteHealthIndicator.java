package com.finova.gateway.health;

import com.finova.gateway.documentation.GatewayRouteCatalog;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reports the size of the routing table so a gateway that lost its Eureka
 * configuration (and therefore serves no route at all) is visible in
 * {@code /actuator/health} instead of failing silently.
 */
@Component("gatewayRoutes")
public class GatewayRouteHealthIndicator implements ReactiveHealthIndicator {

    private static final int MINIMUM_EXPECTED_ROUTES = 1;

    private final GatewayRouteCatalog routeCatalog;

    public GatewayRouteHealthIndicator(GatewayRouteCatalog routeCatalog) {
        this.routeCatalog = routeCatalog;
    }

    @Override
    public Mono<Health> health() {
        return routeCatalog.routeCount()
                .map(count -> count >= MINIMUM_EXPECTED_ROUTES
                        ? Health.up().withDetail("routeCount", count).build()
                        : Health.down().withDetail("routeCount", count)
                        .withDetail("reason", "No gateway route is configured").build());
    }}
