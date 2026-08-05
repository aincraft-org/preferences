allprojects {
    group = "dev.jlo"
    version = "0.1.0-SNAPSHOT"
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
