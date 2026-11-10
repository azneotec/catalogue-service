// Inbound adapter: REST over HTTP + JSON with Spring MVC (public /events, localhost-only /admin).
// Like adapter-persistence, it applies the Spring Boot conventions only for the Boot BOM; it is a
// library, so no boot jar.
plugins {
    id("ticketing.spring-boot-conventions")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.webmvc.ui)

    testImplementation(libs.spring.boot.starter.webmvc.test)
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }
