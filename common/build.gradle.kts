dependencies {
    api(project(":api"))

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
