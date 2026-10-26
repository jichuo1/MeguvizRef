# Provider SPI 1

ProviderDescriptor 包含稳定 ID、实现版本、SPI major/minor、互斥能力、依赖、固定 CALLER_THREAD 和 CODE_SCOPE 关闭所有者。未知 major、重复 ID、未满足依赖和环都在安装时拒绝；同能力多个 Provider 必须用显式 owner 配置裁决。被显式排除的 Provider 不转交 scope，仍由原调用方负责关闭。

ProviderRegistry 是不可变安装快照，只能转交一个 scope，且在首次 resolve 之前安装。快照不支持原地替换；新 scope 安装新快照，旧 scope 按自己的在途计数排空关闭。既有快照在所属作用域内保持不变。

CandidateBatch 只传描述符、代码指纹和候选域；scope 必须已有 STRONG CodeIdentity，并将每个候选重新绑定当前 loader、验证完整类型和请求域。字段/方法 raw 成员不从 Provider 输入接受。错误指纹为 STALE；候选解析失败、候选不在域内为 PARTIAL。

Provider 的 complete=true 只是声明。对本次已支持的结构约束，ProviderCoverageVerifier 以独立直接反射循环重放实际 Java 可见域，核对满足约束的完整 Member 集合；没有证明时仍为 PARTIAL。complete=false 不会被升级。这个冷路径复核有额外枚举成本，因此不将 Provider 冷查询当作性能优化。隐藏过滤仍只代表 Java 可见宇宙。

显式 FIRST 使用 Provider 候选顺序；为证明没有未知候选，Provider 路径不提前停止，并在覆盖复核成功后取第一项。默认结构 FIRST 则在公开反射顺序上直接短路，两种候选来源在 receipt 中区分。

绑定后执行无需候选枚举或 Provider 回调，实际合同套件含 10000 次稳定调用计数验证。测试用 Provider 只验证此 SPI，不构成外部框架的适配实现。

API、SPI major=1、receipt schema major=1 与持久化 envelope 独立版本化。未实现 codegen，不存在已验收的生成 ABI。


[返回文档目录](../README.md)
