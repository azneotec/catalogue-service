// Inbound adapter: gRPC server for other services (ADR-010), implementing ticketing.catalogue.v1
// from ticketing-contracts. Spring Boot 4.1's built-in gRPC support runs the server (ADR-034).
plugins {
    id("ticketing.spring-boot-conventions")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(platform(libs.grpc.bom))
    implementation(libs.ticketing.contracts.grpc)
    implementation(libs.spring.boot.starter.grpc.server)
    // Server reflection, so grpcurl can list and call services without the .proto files.
    implementation(libs.grpc.services)

    testImplementation(platform(libs.grpc.bom))
    testImplementation(libs.grpc.inprocess)
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }
