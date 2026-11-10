package io.github.azneotec.ticketing.catalogue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of catalogue-service. It sits in the base package so Spring's component scan and
 * JPA's entity/repository scan cover every adapter package below it.
 */
@SpringBootApplication
public class CatalogueApplication {

  public static void main(String[] args) {
    SpringApplication.run(CatalogueApplication.class, args);
  }
}
