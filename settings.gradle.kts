pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.library") version "9.4.1"
        id("com.android.application") version "9.4.1"
    }
}
rootProject.name = "MeguvizRef"
val profile = providers.gradleProperty("meguviz.profile").orElse("host").get()
require(profile in setOf("host", "android")) { "meguviz.profile must be host or android" }
include(":ref-core", ":ref-diagnostics")
if (profile == "android") include(":ref-android", ":samples:android-consumer")
