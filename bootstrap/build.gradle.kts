// The runnable Spring Boot application: wires domain, application and adapters together.
plugins {
    id("ticketing.spring-boot-conventions")
}

val ticketingConventionsVersion = providers.gradleProperty("ticketingConventionsVersion").get()

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-persistence"))
    implementation(project(":adapter-rest"))
    implementation(project(":adapter-grpc"))
    implementation(platform(libs.grpc.bom))
    implementation(libs.spring.boot.starter.actuator)
    // Observability (ADR-007, ADR-034): Micrometer Observation -> OpenTelemetry SDK -> OTLP.
    implementation(libs.spring.boot.starter.opentelemetry)
    implementation(libs.datasource.micrometer)
    implementation(libs.otel.logback.appender)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc) // RestClient in the HTTP tests
    testImplementation(platform(libs.grpc.bom))
    testImplementation(libs.ticketing.contracts.grpc) // client stubs for the gRPC test
    testImplementation(libs.grpc.services) // standard health service stubs
    testImplementation(libs.otel.sdk.testing) // in-memory span exporter
    testImplementation(libs.spring.boot.micrometer.tracing.test) // @AutoConfigureTracing
    testImplementation(libs.spring.boot.starter.grpc.server.test) // @LocalGrpcServerPort
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.boot.starter.data.jpa) // JdbcClient in the startup test
    testImplementation("io.github.azneotec.ticketing:ticketing-arch-rules:$ticketingConventionsVersion")
}

tasks.test {
    // -PupdateOpenApi makes OpenApiSpecTest rewrite docs/openapi.yaml instead of only comparing.
    systemProperty("openapi.update", providers.gradleProperty("updateOpenApi").isPresent)
    // The committed spec is a test input: editing it by hand re-runs the test.
    inputs.files(rootProject.file("docs/openapi.yaml")) // files(): may not exist yet
}

// Puts the build's version into application.properties (service.version in telemetry).
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("application.properties") { filter { it.replace("@project.version@", version) } }
}
