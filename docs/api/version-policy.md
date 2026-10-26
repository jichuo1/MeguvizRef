# 版本与兼容性

库版本、Provider SPI 和持久化格式分别管理。稳定版本为 `1.0.0`。

| 边界 | 当前约定 | 检查方式 |
|---|---|---|
| 公开 Java API | 以已提交的 API 基线为准 | `apiDump` 比较公开类型与签名，包括嵌套类型 |
| Provider SPI | major 1 | 拒绝未知 major、无效依赖和不兼容注册 |
| 持久化格式 | schema major 1 | 格式校验、内容身份检查与当前加载器重新解析 |
| 生成代码 ABI | 暂无代码生成器 | 不作生成代码兼容承诺 |

## 公开 API

`verification/api-baseline/` 保存公开签名基线。差异应经过兼容性审查，不应在常规构建中自动覆盖。

`verification/compatibility-clients/` 保存先前编译的 Java/Kotlin 测试客户端及其摘要。`verifyAbi` 使用当前 Maven 制品运行这些客户端，不重新编译它们。它们只用于测试，不随生产库分发。

`CompatibilityPolicy.LEGACY_BIL` 是已有公开枚举名，表示严格装箱匹配策略。名称保留以维持源代码和二进制兼容；具体语义见[核心 API](core-contracts.md)。

## 格式与 SPI

持久化数据不能绕过当前代码身份、加载器和访问策略校验。未知格式版本应明确失败。Provider 的版本协商独立于库版本，不能将提供方的覆盖声明直接视为完整性证明。

[返回文档目录](../README.md)
