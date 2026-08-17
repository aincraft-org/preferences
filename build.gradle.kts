plugins {
    // Applied per subproject that publishes; root stays aggregator-only.
}

allprojects {
    group = "dev.mintychochip"
    version = "0.2.0"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Aggregate convenience: same path CI uses.
tasks.register("ci") {
    group = "verification"
    description = "Build all modules (including :test) and run unit tests"
    dependsOn(
        ":api:build",
        ":common:build",
        ":paper:build",
        ":test:build",
        "test",
    )
}
