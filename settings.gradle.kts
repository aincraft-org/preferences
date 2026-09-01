plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "preferences"

include("preferences-api", "preferences-common", "preferences-paper", "preferences-test")
