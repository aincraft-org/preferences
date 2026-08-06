plugins {
    `maven-publish`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Stable Maven coordinate for hooking plugins: dev.jlo:preferences-api:<version>
tasks.jar {
    archiveBaseName.set("preferences-api")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "preferences-api"
            version = project.version.toString()

            pom {
                name.set("Preferences API")
                description.set(
                    "Public typed-preferences API for Paper plugins (register via PreferencesService).",
                )
                url.set("https://github.com/mintychochip/Preferences")
                licenses {
                    license {
                        name.set("All Rights Reserved")
                    }
                }
                developers {
                    developer {
                        id.set("mintychochip")
                        name.set("mintychochip")
                    }
                }
                scm {
                    url.set("https://github.com/mintychochip/Preferences")
                    connection.set("scm:git:https://github.com/mintychochip/Preferences.git")
                    developerConnection.set("scm:git:https://github.com/mintychochip/Preferences.git")
                }
            }
        }
    }

    repositories {
        // Local: ./gradlew :api:publishToMavenLocal
        // GitHub Packages: GITHUB_ACTOR + GITHUB_TOKEN (needs write:packages)
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/mintychochip/Preferences")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (project.findProperty("gpr.user") as String?)
                    ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: (project.findProperty("gpr.key") as String?)
                    ?: ""
            }
        }
    }
}
