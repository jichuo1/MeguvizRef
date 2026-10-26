plugins { `java-library`; `maven-publish` }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)); withSourcesJar() }
tasks.withType<JavaCompile>().configureEach { options.release.set(17); options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach { isPreserveFileTimestamps = false; isReproducibleFileOrder = true }
dependencies { api(project(":ref-core")) }
dependencyLocking { lockAllConfigurations() }
tasks.register<JavaExec>("contractTests") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.jichuo1.meguviz.ref.diagnostics.DiagnosticsContractTest")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    args(layout.buildDirectory.file("test-results/contracts/TEST-diagnostics.xml").get().asFile.absolutePath)
    workingDir = rootProject.projectDir
}
tasks.check { dependsOn("contractTests") }
publishing {
    publications { create<MavenPublication>("library") { from(components["java"]); artifactId = "meguviz-ref-diagnostics" } }
    repositories { maven { name = "local"; url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI() } }
}
