plugins {
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Exposed ORM
    implementation(libs.bundles.exposed)
    
    // PostgreSQL driver
    implementation(libs.postgresql)
    
    // Coroutines
    implementation(libs.kotlinxCoroutines)
    
    // Serialization (for JSON schema storage)
    implementation(libs.kotlinxSerialization)
    
    // Testing
    testImplementation(kotlin("test"))
}
