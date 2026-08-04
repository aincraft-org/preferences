plugins { `java` }

group = "dev.jlo"
version = "0.1.0-SNAPSHOT"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly(rootProject)
    compileOnly("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }
