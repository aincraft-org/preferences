dependencies {
    api(project(":preferences-api"))

    implementation(libs.caffeine)

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
