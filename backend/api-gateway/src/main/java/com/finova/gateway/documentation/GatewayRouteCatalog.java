package com.finova.gateway.documentation;

import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the live gateway routing table so the aggregated OpenAPI document can
 * never drift away from the configuration in {@code application.yml}.
 */
@Component
public class GatewayRouteCatalog {

    private final RouteDefinitionLocator routeDefinitionLocator;

    public GatewayRouteCatalog(RouteDefinitionLocator routeDefinitionLocator) {
        this.routeDefinitionLocator = routeDefinitionLocator;
    }

    public Mono<List<GatewayRoute>> routes() {
        return routeDefinitionLocator.getRouteDefinitions()
                .mapNotNull(this::toGatewayRoute)
                .sort((left, right) -> left.path().compareTo(right.path()))
                .collectList();
    }

    public Mono<Long> routeCount() {
        return routeDefinitionLocator.getRouteDefinitions().count();
    }

    private GatewayRoute toGatewayRoute(RouteDefinition definition) {
        List<String> patterns = pathPatterns(definition);
        if (patterns.isEmpty()) {
            return null;
        }
        String target = targetOf(definition.getUri());
        return new GatewayRoute(definition.getId(), patterns.get(0), target, filtersOf(definition));
    }

    private List<String> pathPatterns(RouteDefinition definition) {
        List<String> patterns = new ArrayList<>();
        for (PredicateDefinition predicate : definition.getPredicates()) {
            if (!"Path".equals(predicate.getName())) {
                continue;
            }
            for (Object value : predicate.getArgs().values()) {
                for (String pattern : String.valueOf(value).split(",")) {
                    String trimmed = pattern.trim();
                    if (!trimmed.isEmpty()) {
                        patterns.add(trimmed);
                    }
                }
            }
        }
        return patterns;
    }

    private List<String> filtersOf(RouteDefinition definition) {
        List<String> filters = new ArrayList<>();
        for (FilterDefinition filter : definition.getFilters()) {
            filters.add(filter.getName());
        }
        return filters;
    }

    private String targetOf(URI uri) {
        if (uri == null) {
            return "";
        }
        String value = uri.toString();
        return value.toLowerCase(Locale.ROOT).startsWith("lb://")
                ? value.substring("lb://".length())
                : value;
    }

    /**
     * @param id      the route id from {@code spring.cloud.gateway.routes}
     * @param path    the first {@code Path} predicate pattern
     * @param target  the service id behind {@code lb://} or an absolute uri
     * @param filters the gateway filters applied by the route
     */
    public record GatewayRoute(String id, String path, String target, List<String> filters) {

        public boolean isServiceRoute() {
            return path.startsWith("/api/");
        }

        public String docsUrl() {
            return "/docs/" + target + "/v3/api-docs";
        }

        public String swaggerUiUrl() {
            return "/docs/" + target + "/swagger-ui/index.html";
        }
    }
}
