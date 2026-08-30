plugins {
    alias(libs.plugins.run.paper)
}

dependencies {
    // Public registration surface only — never paper/common internals.
    compileOnly(project(":preferences-api"))
    compileOnly(libs.paper.api)
}

tasks.jar {
    archiveBaseName.set("preferences-test")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks {
    runServer {
        minecraftVersion("26.2")
        // Auto-includes this module's jar; also load the Preferences plugin jar.
        pluginJars(project(":preferences-paper").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    }
}
