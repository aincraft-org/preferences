plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

dependencies {
    // Public registration surface only — never paper/common internals.
    compileOnly(project(":api"))
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("preferences-test")
}

tasks {
    runServer {
        minecraftVersion("1.21.7")
        // Auto-includes this module's jar; also load the Preferences plugin jar.
        pluginJars(project(":paper").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    }
}
