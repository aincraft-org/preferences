rootProject.name = "preferences"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/mintychochip/pebblehost-deploy")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
        gradlePluginPortal()
    }
}

include("preferences-api", "preferences-common", "preferences-paper", "preferences-test")
