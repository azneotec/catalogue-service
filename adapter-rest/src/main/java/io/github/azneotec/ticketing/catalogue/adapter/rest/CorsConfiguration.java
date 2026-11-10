package io.github.azneotec.ticketing.catalogue.adapter.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the web shell, served from another origin (Vite on {@code http://localhost:5173}), read the
 * public API from the browser. Only the public read endpoints; the admin API gets no CORS, so a web
 * page can't drive it from a browser. Origins: {@code catalogue.cors.allowed-origins}.
 */
@Component
class CorsConfiguration implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  CorsConfiguration(
      @Value("${catalogue.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins.clone();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/events/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "HEAD")
        // Browsers hide response headers from scripts unless the server exposes them.
        .exposedHeaders(HttpHeaders.ETAG)
        .maxAge(3600);
  }
}
