<div align="center">

# MeguvizRef

**面向 Java 17 与 Android 的反射工具库**

成员解析 · 作用域生命周期 · 不持有接收对象的调用计划

[![CI](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml/badge.svg)](https://github.com/jichuo1/MeguvizRef/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/jichuo1/MeguvizRef)](https://github.com/jichuo1/MeguvizRef/releases)
[![JitPack](https://jitpack.io/v/jichuo1/MeguvizRef.svg)](https://jitpack.io/#jichuo1/MeguvizRef)
![Java 17+](https://img.shields.io/badge/Java-17%2B-orange)
![Android API 27+](https://img.shields.io/badge/Android-API%2027%2B-3DDC84)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)

[快速开始](#快速开始) · [使用示例](#使用示例) · [关键行为](#关键行为) · [文档](#文档)

</div>

---

## ✨ 特性

- **精确与结构查询**：基于描述符定位方法、字段和构造器，默认要求唯一匹配。
- **作用域生命周期**：`CodeScope` 统一管理 loader 与缓存，关闭后拒绝新调用。
- **无接收对象的调用计划**：计划与 receiver 解耦，调用时再传入。
- **零第三方依赖**：核心模块只依赖 JDK。

## 📦 模块

| 模块 | 功能 | 运行时依赖 |
|---|---|---|
| `meguviz-ref-core` | 描述符、精确与结构查询、作用域、调用计划 | 无第三方依赖 |
| `meguviz-ref-diagnostics` | 结构化诊断、代码身份、解析结果持久化 | core |
| `meguviz-ref-android` | Android 平台与代码身份辅助功能 | core |

## 🚀 快速开始

**1. 添加仓库**（`settings.gradle.kts`）

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

**2. 引入依赖**（模块的 `build.gradle.kts`）

```kotlin
dependencies {
    implementation("io.github.jichuo1.meguviz:meguviz-ref-core:1.0.0")
}
```

> [!NOTE]
> 仓库无需认证，当前未发布到 Maven Central。可选模块、Maven XML 与 JitPack 配置见[安装说明](docs/installation.md)。

## 💡 使用示例

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

更多可编译示例：[Java](samples/jvm-consumer) · [Kotlin](samples/kotlin-consumer) · [Android](samples/android-consumer)

## ⚠️ 关键行为

- 结构查询默认要求唯一匹配；覆盖不完整或预算耗尽时不会报告唯一成功。
- 作用域关闭后拒绝新调用，已经进入的目标方法可以完成。
- 默认遵循 Java 访问检查；显式放宽可访问性仍可能被平台拒绝。
- `exportRaw()` 返回的反射对象由调用方管理，关闭作用域无法撤销其外部引用。
- 性能取决于查询方式、缓存命中率和运行环境，不承诺比直接反射更快。

## 📚 文档

| 文档 | 内容 |
|---|---|
| [文档目录](docs/README.md) | 全部文档入口 |
| [核心 API](docs/api/core-contracts.md) | 核心合同与语义 |
| [构建与验证](docs/development.md) | 本地构建、测试与验证流程 |
| [1.0.0 发布说明](docs/releases/1.0.0.md) | 版本内容与变更 |

## 📄 许可证

本项目使用 [Apache License 2.0](LICENSE)，版权与第三方说明见 [NOTICE](NOTICE)。
