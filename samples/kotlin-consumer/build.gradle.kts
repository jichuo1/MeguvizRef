plugins { kotlin("jvm") version "2.4.21"; application }
repositories {
    maven { url = uri(providers.gradleProperty("meguviz.repository").get()) }
    mavenCentral()
}
dependencies { implementation("io.github.jichuo1.meguviz:meguviz-ref-core:${providers.gradleProperty("meguviz.version").get()}") }
dependencyLocking { lockAllConfigurations() }
kotlin { jvmToolchain(17); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
application { mainClass.set("example.KotlinConsumerKt") }
tasks.withType<JavaExec>().configureEach {
    environment = environment.filterKeys { it !in setOf("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS") }
}
tasks.register("runtimeEvidence") {
    dependsOn(tasks.jar)
    doLast {
        layout.buildDirectory.file("runtime-classpath.txt").get().asFile.writeText(configurations.runtimeClasspath.get().asPath)
    }
}
