dependencies {
    api(project(":preferences-api"))

    implementation(libs.caffeine)

    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
