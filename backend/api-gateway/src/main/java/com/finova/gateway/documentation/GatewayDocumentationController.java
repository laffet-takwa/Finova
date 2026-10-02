package com.finova.gateway.documentation;

import com.finova.gateway.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Serves the aggregated platform document.
 * <p>
 * The gateway owns no business endpoints, so it publishes its own OpenAPI
 * document at {@code /v3/api-docs}: one entry per route, each linking to the
 * proxied document of the service that owns it. Swagger UI reads the same URL,
 * so a single {@code /swagger-ui.html} shows the whole platform and still offers
 * the Authorize button.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class GatewayDocumentationController {

    private final GatewayRouteCatalog routeCatalog;

    public GatewayDocumentationController(GatewayRouteCatalog routeCatalog) {
        this.routeCatalog = routeCatalog;
    }

    @GetMapping("/v3/api-docs")
    public Mono<OpenAPI> aggregatedOpenApi() {
        return routeCatalog.routes().map(OpenApiConfig::aggregatedDocument);
    }

    /** Machine readable route table, handy when debugging a routing problem. */
    @GetMapping("/v3/api-docs/routes")
    public Mono<GatewayRouteCatalog.GatewayRoute[]> routeTable() {
        return routeCatalog.routes().map(routes -> routes.toArray(new GatewayRouteCatalog.GatewayRoute[0]));
    }
}
