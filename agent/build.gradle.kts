plugins {
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass = "com.crowsnest.agent.AppKt"
}

dependencies {
    implementation(project(":environment"))
    implementation(project(":database"))
    
    // Koog AI agents
    implementation(libs.koog.agents)
    
    // Ktor client
    implementation(libs.ktor.client.cio)
    
    // Serialization
    implementation(libs.kotlinxSerialization)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(testFixtures(project(":environment")))
    testImplementation(libs.bundles.exposed)
}
