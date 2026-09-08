pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "NEXLIB"
// :app is the Android client. :shared is the Kotlin Multiplatform module it
// depends on. The Compose Desktop module that used to live here was removed —
// the shipping desktop client is the Python one in gdc_desktop/.
include(":app")
include(":shared")
