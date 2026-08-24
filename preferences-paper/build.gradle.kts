plugins {
    `maven-publish`
}

dependencies {
    implementation(project(":preferences-common"))

    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
}

// Ship api + common classes inside the Preferences plugin jar so a single
// plugin load brings the full runtime (public API + internals). Also embed
// common's runtime deps that Paper does not provide (Caffeine).
tasks.jar {
    dependsOn(":preferences-api:classes", ":preferences-common:classes")
    from(project(":preferences-api").sourceSets["main"].output)
    from(project(":preferences-common").sourceSets["main"].output)
    from({
        project(":preferences-common").configurations["runtimeClasspath"]
            .filter { it.name.startsWith("caffeine") }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("preferences")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
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
            artifactId = "preferences-paper"
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
