# 安装

要求：JDK 17；Android 模块要求 API 27 或更高版本。


推荐使用本仓库的公开 Maven 仓库，无需令牌或登录。这里只开放本库 group，避免覆盖其他依赖来源：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/jichuo1/MeguvizRef/maven/") {
            content { includeGroup("io.github.jichuo1.meguviz") }
        }
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jichuo1.meguviz:meguviz-ref-core:1.0.0")
    // 可选：结构化诊断与持久化收据
    implementation("io.github.jichuo1.meguviz:meguviz-ref-diagnostics:1.0.0")
    // 可选：Android API 27+ 辅助功能
    implementation("io.github.jichuo1.meguviz:meguviz-ref-android:1.0.0")
}
```

Maven 消费者：

```xml
<repositories>
  <repository>
    <id>meguviz</id>
    <url>https://raw.githubusercontent.com/jichuo1/MeguvizRef/maven/</url>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>io.github.jichuo1.meguviz</groupId>
    <artifactId>meguviz-ref-core</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

JitPack 使用独立坐标，不应与上述坐标混用：

```kotlin
repositories {
    maven("https://jitpack.io") {
        content { includeGroup("com.github.jichuo1.MeguvizRef") }
    }
}
dependencies {
    implementation("com.github.jichuo1.MeguvizRef:meguviz-ref-core:1.0.0")
    // 同组另有 meguviz-ref-diagnostics 与 meguviz-ref-android
}
```

此处的公开 Maven 是 GitHub 托管的标准 Maven 仓库，**不表示已发布到 Maven Central**。

## 模块选择

核心模块可单独使用。诊断与 Android 模块都依赖 core，按需引入即可。只在项目中选择一种仓库坐标体系，避免将同一套类重复加入运行时。

[返回文档目录](README.md)
