plugins {
    `maven-publish`
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
    compileOnly(libs.guava)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.guava)
    testRuntimeOnly(libs.junit.platform.launcher)
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
        // Local: ./gradlew :preferences-api:publishToMavenLocal
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
