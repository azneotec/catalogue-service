// Outbound adapter: Spring Data JPA (Hibernate) + Flyway on Postgres.
// Applies the Spring Boot conventions only to get the Spring Boot BOM from one place
// (no version duplicated here). It is a library, not an application, so no boot jar.
plugins {
    id("ticketing.spring-boot-conventions")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }
