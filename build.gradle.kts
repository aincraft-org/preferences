plugins {
    // Applied per subproject that publishes; root stays aggregator-only.
}

allprojects {
    group = "dev.mintychochip"
    // CalVer: YYYY.MM.DD.<github_run_number> in CI; dated -SNAPSHOT locally.
    // buildVersion is an explicit release override (see ci-release skill).
    val calverDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    version = providers.gradleProperty("buildVersion")
        .orElse(
            providers.environmentVariable("GITHUB_RUN_NUMBER")
                .map { "$calverDate.$it" }
        )
        .orElse("$calverDate-SNAPSHOT")
        .get()
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Aggregate convenience: same path CI uses.
tasks.register("ci") {
    group = "verification"
    description = "Build all modules (including :preferences-test) and run unit tests"
    dependsOn(
        ":preferences-api:build",
        ":preferences-common:build",
        ":preferences-paper:build",
        ":preferences-test:build",
        ":preferences-api:test",
        ":preferences-common:test",
    )
}
