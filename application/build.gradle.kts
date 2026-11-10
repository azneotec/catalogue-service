// Use cases and ports. Plain Java; depends on the domain only.
plugins {
    id("ticketing.java-conventions")
}

dependencies {
    implementation(project(":domain"))
}
