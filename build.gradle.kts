// Root build file - applies shared configuration to all subprojects
plugins {
    base
}

// Apply the convention plugin to ALL Kotlin subprojects automatically
subprojects {
    // All subprojects should use the convention plugin
    apply(plugin = "buildsrc.convention.kotlin-jvm")
}

// Global test task that runs tests in all modules
tasks.register("testAll") {
    group = "verification"
    description = "Runs tests in all modules"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("test") })
}
