// Root of the repo: no code here, only the shared version (from Git tags) for every module.
plugins {
    base
}

allprojects {
    apply(from = rootProject.file("gradle/version.gradle.kts"))
}
