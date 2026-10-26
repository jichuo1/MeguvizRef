# 持久化 schema 1.0

`StoredResolution` 保存 strong CodeIdentity、完整 MemberDescriptor、固定 AccessPolicy 和 modifier 约束。query SHA-256 覆盖完整 exact query、策略与 modifiers；代码 SHA-256 覆盖有序 artifact 身份、加载配置和规则版本。哈希用于身份/损坏检查，不是签名或可信执行授权。

仅 `exact_descriptor_hint` 可恢复。`diagnostic_only` 不能进入执行；FIRST/UNIQUE/ALL 的覆盖结论不被跨进程提升为完整证明。provider complete 是上游声明；`IndependentVerifier.verifyFiniteUniverse` 用独立公开枚举对有限已知夹具核验完整候选集合，能发现遗漏。

Codec 限制 64 KiB、深度16、4096个值节点、8192字符字符串、255参数。拒绝重复键、错误UTF-8、未知major/minor、额外字段、截断、浮点数和超限输入。当前minor没有可忽略字段，因此未知minor也拒绝。未来扩展需更新schema合同与兼容夹具。

FileReceiptStore 使用 key SHA-256 文件名，要求调用者独占目录，限制条目；不跨多个写入进程协调容量。保存先写唯一临时文件、force数据、检查中断，再原子替换。无法原子替换时保留旧记录并报告 IO 失败。测试覆盖替换、容量、中断和跨独立进程重开。

恢复必须传入对应scope的同一loader和实际当前强身份。库检查loader与访问策略；代码加载系统仍负责代码集合变更与scope epoch同步。持久化文件不是安全权限边界，不能将不可信hash宣称为已核验代码。


[返回文档目录](../README.md)
