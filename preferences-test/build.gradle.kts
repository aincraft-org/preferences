plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("dev.pebblehost.deploy") version "2026.08.21"
}

dependencies {
    // Public registration surface only — never paper/common internals.
    compileOnly(project(":preferences-api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
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

pebblehost {
    // Deploy the built test plugin jar (incremental: tracks the jar task output).
    jar = tasks.jar.flatMap { it.archiveFile }
    targetDir = "plugins"
    strategy = "groups"
    canaryGate = true
    continueAfterCanary = false
    restart = true
    verifyState = "running"
    verifyTimeoutMs = 180_000
    rollback = "abort"

    val raw = (project.findProperty("pebblehostTargets") as? String) ?: ""
    pbBinary = (project.findProperty("pebblehostPbBinary") as? String) ?: "pb"
    val configuredToken = project.findProperty("pebblehostToken") as? String
    if (!configuredToken.isNullOrBlank()) {
        token = configuredToken
    }
    if (raw.isNotBlank()) {
        raw.split(",").filter { it.isNotBlank() }.forEach { spec ->
            val parts = spec.split(":", limit = 2)
            val targetServerId = parts[0]
            val targetGroup = parts.getOrElse(1) { "default" }
            targets.add(objects.newInstance(dev.pebblehost.deploy.Target::class.java).apply {
                serverId.set(targetServerId)
                group.set(targetGroup)
            })
        }
    }
}
