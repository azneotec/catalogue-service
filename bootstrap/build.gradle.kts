// The runnable Spring Boot application: wires domain, application and adapters together.
plugins {
    id("ticketing.spring-boot-conventions")
}

val ticketingConventionsVersion = providers.gradleProperty("ticketingConventionsVersion").get()

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":adapter-persistence"))
    implementation(libs.spring.boot.starter.actuator)
    // Web server only for actuator health in session 006; REST endpoints arrive in session 007.
    implementation(libs.spring.boot.starter.webmvc)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.boot.starter.data.jpa) // JdbcClient in the startup test
    testImplementation("io.github.azneotec.ticketing:ticketing-arch-rules:$ticketingConventionsVersion")
}
