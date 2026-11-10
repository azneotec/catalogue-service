package io.github.azneotec.ticketing.catalogue.adapter.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets only loopback callers (127.0.0.0/8, ::1) reach {@code /admin/**}; everyone else gets 403.
 * Stands in for authentication until Keycloak roles replace it in Phase 2 (ADR-033).
 *
 * <p>It is a Spring MVC interceptor, Spring's filter around controllers, rather than a servlet
 * filter: it is matched by the same path patterns as the controllers, so no path spelling ({@code
 * //admin}, {@code /admin;x}) can reach an admin controller without passing it.
 *
 * <p>It trusts the TCP peer address only. {@code X-Forwarded-For} is ignored (Spring Boot doesn't
 * apply forwarded headers unless configured), so a caller can't claim to be localhost.
 */
@Component
class LocalhostOnlyAdminInterceptor implements HandlerInterceptor, WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/admin", "/admin/**");
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isLoopback(request.getRemoteAddr())) {
      throw Problems.exception(
          HttpStatus.FORBIDDEN,
          "admin-localhost-only",
          "Admin API is localhost only",
          "The admin API accepts calls from this machine only until authentication arrives");
    }
    return true;
  }

  static boolean isLoopback(String address) {
    if (address == null) {
      return false;
    }
    try {
      // ofLiteral parses an IP literal and never does a DNS lookup.
      return InetAddress.ofLiteral(address).isLoopbackAddress();
    } catch (IllegalArgumentException notAnIpLiteral) {
      return false;
    }
  }
}
