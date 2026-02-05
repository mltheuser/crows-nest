plugins {
    alias(libs.plugins.kotlinPluginSerialization)
    `java-test-fixtures`
}

dependencies {
    // Kotlinx ecosystem
    implementation(libs.bundles.kotlinxEcosystem)
    
    // Browser automation
    implementation(libs.kdriver)
    
    // HTML parsing
    implementation(libs.jsoup)
    implementation(libs.khtmltomarkdown)
    
    // Ktor server (for test fixtures)
    implementation(libs.ktor.server.netty)

    // Test fixtures dependencies (shared with other modules)
    testFixturesImplementation(libs.ktor.server.netty)
    testFixturesImplementation(libs.kotlinxSerialization)

    testImplementation(kotlin("test"))
}