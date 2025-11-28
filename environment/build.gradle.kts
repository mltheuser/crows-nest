plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.koog.agents)
    implementation("dev.kdriver:core:0.5.0")
    implementation("com.mohamedrejeb.ksoup:ksoup-html:0.6.0")
    implementation("io.ktor:ktor-server-netty:3.3.2")
    implementation("io.github.mltheuser:khtmltomarkdown:1.+")

    testImplementation(kotlin("test"))
}