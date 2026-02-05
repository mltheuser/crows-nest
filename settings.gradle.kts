dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

include(":agent")
include(":environment")
include(":database")
include(":matcher")
include(":web")

rootProject.name = "CrowsNest"