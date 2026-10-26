plugins { id("com.android.library"); `maven-publish` }
android {
    namespace = "io.github.jichuo1.meguviz.ref.android"
    compileSdk = 37
    enableKotlin = false
    defaultConfig { minSdk = 27; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    publishing { singleVariant("release") { withSourcesJar() } }
    packaging.resources.excludes.removeAll(setOf("/META-INF/LICENSE", "/META-INF/NOTICE"))
}
dependencies { api(project(":ref-core")) }
dependencyLocking { lockAllConfigurations() }
afterEvaluate {
    publishing {
        publications { create<MavenPublication>("release") { from(components["release"]); artifactId = "meguviz-ref-android" } }
        repositories { maven { name = "local"; url = rootProject.layout.buildDirectory.dir("local-maven").get().asFile.toURI() } }
    }
}
