plugins {
    `maven-publish`
}

dependencies {
    implementation(project(":common"))

    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("org.jspecify:jspecify:1.0.0")
}

// Ship api + common classes inside the Preferences plugin jar so a single
// plugin load brings the full runtime (public API + internals). Also embed
// common's runtime deps that Paper does not provide (Caffeine).
tasks.jar {
    dependsOn(":api:classes", ":common:classes")
    from(project(":api").sourceSets["main"].output)
    from(project(":common").sourceSets["main"].output)
    from({
        project(":common").configurations["runtimeClasspath"]
            .filter { it.name.startsWith("caffeine") }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("preferences")
}

// Optional coordinate for the shippable plugin jar (not required for API consumers).
publishing {
    publications {
        create<MavenPublication>("maven") {
            // Publish the fat plugin jar only (not the plain component) so consumers
            // get the same artifact drop that run-paper / plugins/ would load.
            artifact(tasks.jar) {
                extension = "jar"
            }
            artifact(tasks.named("sourcesJar"))
            groupId = project.group.toString()
            artifactId = "preferences"
            version = project.version.toString()

            pom {
                name.set("Preferences")
                description.set("Paper plugin: typed preferences with dialog GUI and YAML persistence.")
                url.set("https://github.com/aincraft-org/preferences")
            }
        }
    }

    repositories {
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
