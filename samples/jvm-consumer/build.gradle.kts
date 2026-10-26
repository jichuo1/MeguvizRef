plugins { application }
repositories { maven { url = uri(providers.gradleProperty("meguviz.repository").get()) } }
dependencies { implementation("io.github.jichuo1.meguviz:meguviz-ref-core:${providers.gradleProperty("meguviz.version").get()}") }
dependencyLocking { lockAllConfigurations() }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
application { mainClass.set("example.Consumer") }
tasks.withType<JavaExec>().configureEach {
    environment = environment.filterKeys { it !in setOf("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS") }
}
tasks.register<JavaExec>("verifyConsumer") {
    dependsOn(tasks.classes)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.Consumer")
    args(layout.buildDirectory.file("consumer.json").get().asFile.absolutePath)
}
