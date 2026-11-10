package io.github.azneotec.ticketing.catalogue;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The whole service on a random port against one real Postgres. Every integration test extends
 * this, so they share one container and one cached Spring context (same configuration = same cache
 * key) instead of starting one of each per test class.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.grpc.server.port=0") // random free port, like the HTTP server
abstract class IntegrationTestBase {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.6-alpine")
          .withCreateContainerCmdModifier(
              cmd -> cmd.getHostConfig().withMemory(256L * 1024 * 1024));

  static {
    POSTGRES.start();
  }
}
