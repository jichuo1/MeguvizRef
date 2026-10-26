# MeguvizRef

[![CI](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml/badge.svg)](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/jichuo1/MeguvizRef)](https://github.com/jichuo1/MeguvizRef/releases)
[![JitPack](https://jitpack.io/v/jichuo1/MeguvizRef.svg)](https://jitpack.io/#jichuo1/MeguvizRef)

Java 17 成员解析、作用域生命周期和不持有 receiver 的反射调用计划。核心模块没有第三方运行时依赖；诊断和 Android 支持为可选模块。当前稳定版本 **1.0.0**，许可证 Apache-2.0。

## 安装

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

## 使用

```java
import io.github.jichuo1.meguviz.ref.api.descriptor.MemberDescriptor;
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec;
import io.github.jichuo1.meguviz.ref.api.result.ResolutionResult;
import io.github.jichuo1.meguviz.ref.runtime.invoke.MethodPlan;
import io.github.jichuo1.meguviz.ref.scope.CodeScope;

MemberDescriptor descriptor = MemberDescriptor.method(
    "java.lang.String", "substring", "Ljava/lang/String;", "I");
try (CodeScope scope = CodeScope.open(String.class.getClassLoader())) {
    ResolutionResult result = scope.resolve(QuerySpec.exact(descriptor));
    MethodPlan plan = scope.bindMethod(result.requireSingle());
    Object value = plan.invoke("hello", new Object[] {2}); // "llo"
}
```

完整可编译用例在 [Java 消费者](samples/jvm-consumer) 和 [Kotlin 消费者](samples/kotlin-consumer)。公开 API 基线和旧版编译客户端在 `verification/`。

## 行为边界

- 结构查询默认要求唯一匹配；预算耗尽或覆盖不完整不能证明唯一。
- `CodeScope.close()` 拒绝新调用，已进入的目标调用可以结束；旧 token 不能跨 scope/代际复用。
- 默认遵循 Java 访问检查。主动请求抑制访问检查仍可能失败。
- `exportRaw()` 将反射对象所有权交给调用方，其生命周期不再由 scope 完全控制。
- Android 模块支持 API 27+。库不提供 Hook 引擎、DEX 搜索后端、KavaRef 二进制命名空间兼容或宿主版本适配。
- 已有设备和消费者集成验收范围见 [1.0.0 发布说明](docs/releases/1.0.0.md)。CI 构建通过不代表所有设备或宿主功能都得到运行时认证。

## 构建与校验

需要 JDK 17。纯 JVM 构建不需要 Android SDK：

```sh
bash gradlew verifyHost --build-cache --no-daemon
# Android SDK platform 37；同时执行 lint 与缩减后的消费应用构建
bash gradlew -Pmeguviz.profile=android verifyHost verifyAndroid --build-cache --no-daemon
# 相同调用内生成三个模块的 Maven 文件、源码包及 SHA256SUMS
bash gradlew -Pmeguviz.profile=android packageRelease --build-cache --no-daemon
```

CI 仅在 main/PR 的构建相关变动运行；同一分支旧运行会取消。单次 Gradle 调用复用本地发布产物，统一 Gradle 缓存，最多两个 worker。标签发布不重复触发 main CI；同一次构建的字节用于 Release 和 Maven，已有版本禁止覆盖。JitPack 仅编译库、运行核心合同并安装 Maven 制品，不构建示例或重复完整发布流水线。
