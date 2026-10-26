# 构建与验证

## 工具链

- JDK 17；Gradle Wrapper 固定构建工具版本。
- JVM 模式不要求 Android SDK。
- Android 模式使用 SDK `platforms;android-37.0` 与 `build-tools;37.0.0`。CI 还固定了 SDK 命令行工具版本。

## 常用命令

在项目根目录执行：

```sh
# 核心合同、独立 Java/Kotlin 消费者、公开 API 和旧客户端兼容性
bash gradlew verifyHost --build-cache --no-daemon

# 增加 Android 库 lint 与 R8 消费应用构建
bash gradlew -Pmeguviz.profile=android verifyHost verifyAndroid --build-cache --no-daemon

# 验证后生成三个模块的 Maven 制品、源码包及校验和
bash gradlew -Pmeguviz.profile=android packageRelease --build-cache --no-daemon
```

Windows 使用 `gradlew.bat` 替换 `bash gradlew`；PowerShell 中带点号的 `-P` 参数应整体加引号，例如 `'-Pmeguviz.profile=android'`。

`build/local-maven/` 保存本次构建的 Maven 仓库；`build/release/` 保存发布文件。合同测试结果位于各模块的 `build/test-results/`。独立夹具说明见 [test-fixtures](../test-fixtures/README.md)。

## CI 与发布

main 和拉取请求的构建相关变更运行 CI；纯文档变更不触发完整构建。同一分支的新运行会取消过期 CI。

校验合并为一次 Gradle 调用，使用统一缓存和两个 worker。包含库源码变更的发布仍需通过合同、独立消费者、API、ABI、Android lint 与 R8 检查。

稳定版本标签应与 `gradle.properties` 一致。发布工作流在验证后，将同一批产物写入公开 Maven 分支和 GitHub Release；已有 Maven 版本不能被不同内容覆盖。JitPack 使用标签源码独立构建库模块。

## 提交范围

只提交源码、构建配置、文档与可公开的测试夹具。凭据、本机配置、签名材料、设备标识及运行记录应保留在仓库之外。忽略规则不能清理已经提交的历史，提交前仍需检查暂存区。

[返回文档目录](README.md)
