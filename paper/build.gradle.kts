dependencies {
    implementation(project(":common"))

    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    compileOnly("org.jspecify:jspecify:1.0.0")
}

// Ship api + common classes inside the Preferences plugin jar so a single
// plugin load brings the full runtime (public API + internals).
tasks.jar {
    dependsOn(":api:classes", ":common:classes")
    from(project(":api").sourceSets["main"].output)
    from(project(":common").sourceSets["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Prefer a stable archive name for run-paper / plugins/ drops.
tasks.jar {
    archiveBaseName.set("preferences")
}
