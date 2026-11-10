// catalogue-service: events, venues and seat maps (Phase 1).
// Convention plugins and ticketing-arch-rules come from ticketing-build-conventions via mavenLocal.
pluginManagement {
    val ticketingConventionsVersion = providers.gradleProperty("ticketingConventionsVersion").get()
    repositories {
        mavenLocal() // temporary, until the conventions are published to a real repository
        gradlePluginPortal()
    }
    plugins {
        id("ticketing.java-conventions") version ticketingConventionsVersion
        id("ticketing.domain-conventions") version ticketingConventionsVersion
        id("ticketing.spring-boot-conventions") version ticketingConventionsVersion
    }
}

// The convention plugins declare no repositories; this build declares them once, here.
dependencyResolutionManagement {
    repositories {
        mavenLocal() // ticketing-arch-rules
        mavenCentral()
    }
}

rootProject.name = "catalogue-service"

include("domain", "application", "adapter-persistence", "bootstrap")
