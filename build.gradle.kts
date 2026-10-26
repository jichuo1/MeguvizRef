import org.gradle.api.tasks.GradleBuild
import org.gradle.api.publish.maven.MavenPublication

plugins { base }
allprojects {
    group = "io.github.jichuo1.meguviz"
    version = providers.gradleProperty("meguviz.version").get()
    repositories { google(); mavenCentral() }
    tasks.withType<JavaExec>().configureEach {
        environment = environment.filterKeys { it !in setOf("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS") }
    }
    tasks.configureEach { if (name != "verificationStart") mustRunAfter(":verificationStart") }
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("MeguvizRef ${project.name}")
                    description.set("Scoped JVM reflection resolution and receiver-free invocation plans")
                    url.set("https://github.com/jichuo1/MeguvizRef")
                    licenses { license { name.set("Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
                    developers { developer { id.set("jichuo1"); name.set("jichuo1") } }
                    scm {
                        url.set("https://github.com/jichuo1/MeguvizRef")
                        connection.set("scm:git:https://github.com/jichuo1/MeguvizRef.git")
                        developerConnection.set("scm:git:ssh://git@github.com/jichuo1/MeguvizRef.git")
                    }
                }
            }
        }
    }
}
val python = providers.gradleProperty("meguviz.python").orElse("python")
val profile = providers.gradleProperty("meguviz.profile").orElse("host")
tasks.register<Exec>("verificationStart") {
    commandLine(python.get(), "tools/build_gate.py", "begin", "--version", version.toString())
}
val publishLocal = tasks.register("publishLocal") {
    dependsOn(":ref-core:publishAllPublicationsToLocalRepository", ":ref-diagnostics:publishAllPublicationsToLocalRepository")
    if (profile.get() == "android") dependsOn(":ref-android:publishAllPublicationsToLocalRepository")
}
fun independentConsumer(taskName: String, directory: String, targets: List<String>) = tasks.register<GradleBuild>(taskName) {
    dependsOn(publishLocal)
    dir = file(directory)
    tasks = targets
    startParameter.projectProperties = mapOf("meguviz.repository" to layout.buildDirectory.dir("local-maven").get().asFile.absolutePath,
        "meguviz.version" to version.toString())
}
val javaConsumer = independentConsumer("verifyJvmConsumer", "samples/jvm-consumer", listOf("verifyConsumer"))
val kotlinConsumer = independentConsumer("verifyKotlinConsumer", "samples/kotlin-consumer", listOf("run", "runtimeEvidence"))
tasks.register<Exec>("apiDump") {
    dependsOn(":ref-core:jar", ":ref-diagnostics:jar")
    commandLine(python.get(), "tools/build_gate.py", "api", "--version", version.toString())
}
tasks.register<Exec>("verifyAbi") {
    dependsOn(publishLocal, kotlinConsumer)
    commandLine(python.get(), "tools/build_abi.py", "verify", "--version", version.toString())
}
tasks.register<Exec>("verifyHost") {
    group = "verification"
    dependsOn("verificationStart", ":ref-core:contractTests", ":ref-diagnostics:contractTests", javaConsumer, kotlinConsumer, "apiDump", "verifyAbi")
    commandLine(python.get(), "tools/build_gate.py", "host", "--version", version.toString())
}
tasks.register("verifyAndroid") {
    group = "verification"
    require(profile.get() == "android" || !gradle.startParameter.taskNames.any { it.endsWith("verifyAndroid") }) { "verifyAndroid requires -Pmeguviz.profile=android" }
    if (profile.get() == "android") dependsOn(":ref-android:assembleRelease", ":ref-android:lintRelease", ":samples:android-consumer:assembleRelease", ":samples:android-consumer:releaseDependencyReport")
}
tasks.register<Exec>("packageRelease") {
    group = "distribution"
    dependsOn("verifyHost", "verifyAndroid", publishLocal)
    commandLine(python.get(), "tools/package_release.py", "--version", version.toString())
}
