plugins { id("com.android.application") }
abstract class PrepareDexFixture : Exec() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}
repositories { maven { url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI() } }
android {
    namespace = "example.meguviz"
    compileSdk = 37
    enableKotlin = false
    defaultConfig {
        applicationId = "example.meguviz"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "example.meguviz.ContractInstrumentation"
        testProguardFiles("test-rules.pro")
    }
    testBuildType = "release"
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
val dexFixture = tasks.register<PrepareDexFixture>("prepareDexFixture") {
    inputs.file(file("fixture-src/fixture/SameName.java"))
    inputs.file(rootProject.file("tools/build_android_fixture.py"))
    outputDirectory.set(layout.buildDirectory.dir("generated/dex-assets"))
    commandLine(providers.gradleProperty("meguviz.python").orElse("python").get(), rootProject.file("tools/build_android_fixture.py").absolutePath)
}
androidComponents.onVariants { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(dexFixture) { it.outputDirectory }
}
dependencies {
    implementation("io.github.jichuo1.meguviz:meguviz-ref-core:${project.version}")
    implementation("io.github.jichuo1.meguviz:meguviz-ref-android:${project.version}")
    // Isolated instrumentation benchmark baseline; never part of the application/POM.
    androidTestImplementation("com.highcapable.kavaref:kavaref-core:1.1.1")
    androidTestRuntimeOnly("com.highcapable.kavaref:kavaref-android:1.1.1")
}
dependencyLocking { lockAllConfigurations() }
tasks.register("releaseDependencyReport") {
    dependsOn(":publishLocal")
    val output = layout.buildDirectory.file("releaseRuntimeClasspath.txt")
    outputs.file(output)
    doLast {
        val report = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
            .map { it.id.displayName }.sorted().joinToString("\n")
        output.get().asFile.apply { parentFile.mkdirs(); writeText(report + "\n") }
    }
}
tasks.configureEach {
    if (name.startsWith("pre") && name.endsWith("Build")) dependsOn(":ref-core:publishAllPublicationsToLocalRepository", ":ref-android:publishAllPublicationsToLocalRepository")
}
