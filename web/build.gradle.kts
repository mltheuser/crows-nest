plugins {
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

application {
    mainClass = "com.crowsnest.web.AppKt"
}

dependencies {
    implementation(project(":database"))
    
    // Ktor Server
    implementation(libs.bundles.ktorServer)
    
    // Ktor Client (for OAuth)
    implementation(libs.bundles.ktorClient)
    
    // Password hashing
    implementation(libs.bcrypt)
    
    // Coroutines & Serialization
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerialization)
    
    // HTML DSL
    implementation(libs.kotlinxHtml)
    
    // Testing
    testImplementation(kotlin("test"))
}
