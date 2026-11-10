package io.github.azneotec.ticketing.catalogue.adapter.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Header of the OpenAPI document springdoc generates from the controllers. The server URL is fixed
 * so the committed {@code docs/openapi.yaml} doesn't change with the port a test happens to use.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

  @Bean
  OpenAPI catalogueOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Catalogue API")
                .version("1")
                .description(
                    "Events, venues and seat maps. Errors are Problem Details (RFC 9457). "
                        + "/admin is localhost only until Phase 2."))
        .servers(List.of(new Server().url("http://localhost:8080").description("local")));
  }
}
