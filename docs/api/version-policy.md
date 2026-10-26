# 四条兼容线

公共 API：当前候选为1.0.0-rc.1；`verification/api-baseline/ref-core.txt` 与 `ref-diagnostics.txt` 保留首次冻结的公共API快照，旧0.1编译客户端继续与当前实际制品链接运行。`apiDump` 对比变更，差异必须显式审查，不能自动覆盖。

首次候选冻结审查更正：初始收集脚本排除了所有含`$`的类名，遗漏公开Builder/enum及结果嵌套类。2026-10-26经明确审查授权，在首份候选完成前改为遍历全部classfile、仅保留javap公开声明，并显式重建初始baseline。未重新编译或替换已冻结的Java/Kotlin黄金客户端JAR；这次修正扩充API观测范围，不能用旧不完整dump证明兼容。

SPI：major1，未知major由安装测试拒绝；provider元数据与安装快照合同独立于核心artifact版本。

持久化：schema major1，未知major和弱身份拒绝，恢复必须绑定当前loader和内容身份；FileReceiptStore和实际子进程重放由诊断合同验证。

生成 ABI：尚无生成器，not_applicable，不伪称兼容。

`verification/compatibility-clients/` 保存第一次编译的Java17及Kotlin客户端JAR和SHA256。`verifyAbi`不重新编译这些JAR，只使用当前本地Maven制品启动旧客户端，共5个断言。客户端JAR不进入生产发布。当前只建立首份黄金兼容线，不能宣称已验证多个历史发布版本。
