package io.github.azneotec.ticketing.catalogue.adapter.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests against a real Postgres in Docker, the same image as the platform's Compose file.
 *
 * <p>Lifecycle: "singleton container". It starts once per test JVM (static initialiser) and is
 * never stopped by us; Testcontainers' reaper container (Ryuk) removes it when the JVM exits. This
 * matches Spring's test-context cache, which keeps one application context alive across test
 * classes: a container stopped after the first class would leave that cached context pointing at a
 * dead port. @ServiceConnection hands the container's URL and credentials to Spring Boot.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
abstract class PostgresTestBase {

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.6-alpine")
          // Memory limit on every container (AGENTS.md). 256 MB is plenty for tests.
          .withCreateContainerCmdModifier(
              cmd -> cmd.getHostConfig().withMemory(256L * 1024 * 1024));

  static {
    POSTGRES.start();
  }

  @Autowired JdbcClient jdbc;

  /** Real commits happen in these tests, so every test starts from empty tables. */
  @BeforeEach
  void cleanTables() {
    jdbc.sql("truncate table event, venue").update();
  }
}
