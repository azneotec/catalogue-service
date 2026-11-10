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
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc) // RestClient in the HTTP tests
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
