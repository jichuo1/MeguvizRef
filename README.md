# MeguvizRef

[![CI](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml/badge.svg)](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/jichuo1/MeguvizRef)](https://github.com/jichuo1/MeguvizRef/releases)
[![JitPack](https://jitpack.io/v/jichuo1/MeguvizRef.svg)](https://jitpack.io/#jichuo1/MeguvizRef)

面向 Java 17 与 Android 的反射工具库，提供成员解析、作用域生命周期和不持有接收对象的调用计划。

**稳定版本：1.0.0 · Java 17+ · Android API 27+ · Apache-2.0**

## 模块

| 模块 | 功能 | 运行时依赖 |
|---|---|---|
| `meguviz-ref-core` | 描述符、精确与结构查询、作用域、调用计划 | 无第三方依赖 |
| `meguviz-ref-diagnostics` | 结构化诊断、代码身份、解析结果持久化 | core |
| `meguviz-ref-android` | Android 平台与代码身份辅助功能 | core |

## 快速开始

在 `settings.gradle.kts` 中加入公开 Maven 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/jichuo1/MeguvizRef/maven/") {
            content { includeGroup("io.github.jichuo1.meguviz") }
        }
    }
}
```

在模块的 `build.gradle.kts` 中引入核心库：

```kotlin
dependencies {
    implementation("io.github.jichuo1.meguviz:meguviz-ref-core:1.0.0")
}
```

该仓库无需认证。可选模块、Maven XML 与 JitPack 配置见[安装说明](docs/installation.md)。当前未发布到 Maven Central。

## 使用示例


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

更多可编译示例：[Java](samples/jvm-consumer)、[Kotlin](samples/kotlin-consumer)、[Android](samples/android-consumer)。

## 关键行为

- 结构查询默认要求唯一匹配；覆盖不完整或预算耗尽时不会报告唯一成功。
- 作用域关闭后拒绝新调用，已经进入的目标方法可以完成。
- 默认遵循 Java 访问检查；显式请求调整可访问性仍可能被平台拒绝。
- `exportRaw()` 返回的反射对象由调用方管理，关闭作用域不能撤销其外部引用。
- 性能取决于查询方式、缓存命中率和运行环境；不承诺所有调用都比直接反射更快。

## 文档

[文档目录](docs/README.md) · [核心 API](docs/api/core-contracts.md) · [构建与验证](docs/development.md) · [1.0.0 发布说明](docs/releases/1.0.0.md)

## 许可证

本项目使用 [Apache License 2.0](LICENSE)，版权与第三方说明见 [NOTICE](NOTICE)。
