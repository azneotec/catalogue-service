package io.github.azneotec.ticketing.catalogue;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Connects the {@code OTEL} Logback appender (declared in {@code logback-spring.xml}) to the
 * OpenTelemetry SDK that Spring Boot builds, so every log record also goes out over OTLP to Loki,
 * carrying the trace id and span id of the request that wrote it. Logback starts before Spring, so
 * the appender buffers nothing and drops records until this runs.
 */
@Component
class OpenTelemetryLogging implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  OpenTelemetryLogging(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
