rootProject.name = "preferences"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/mintychochip/pebblehost-deploy")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: extra["gpr.user"]?.toString()
                password = System.getenv("GITHUB_TOKEN")
                    ?: extra["gpr.key"]?.toString()
            }
        }
        gradlePluginPortal()
    }
}

include("preferences-api", "preferences-common", "preferences-paper", "test")
