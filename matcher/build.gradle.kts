plugins {
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass = "com.crowsnest.matcher.AppKt"
}

dependencies {
    implementation(project(":database"))
    
    // Koog for LLM prompts
    implementation(libs.koog.agents)
    
    // Ktor client
    implementation(libs.bundles.ktorClient)
    
    // Coroutines
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerialization)
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.postgresql)
    testImplementation(libs.bundles.exposed)
}
