plugins {
    `maven-publish`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Stable Maven coordinate for hooking plugins: dev.mintychochip:preferences-api:<version>
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
                url.set("https://github.com/aincraft-org/preferences")
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
                    url.set("https://github.com/aincraft-org/preferences")
                    connection.set("scm:git:https://github.com/aincraft-org/preferences.git")
                    developerConnection.set("scm:git:https://github.com/aincraft-org/preferences.git")
                }
            }
        }
    }

    repositories {
        // Local: ./gradlew :api:publishToMavenLocal
        // GitHub Packages: GITHUB_ACTOR + GITHUB_TOKEN (needs write:packages)
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aincraft-org/preferences")
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
