# 核心 API 与行为约定

实现为 Java17 字节码、零第三方生产依赖；Kotlin 消费者通过正常 Java 互操作使用。公开签名基线和兼容性检查见[版本与兼容性](version-policy.md)。

`TypeDescriptor.of` 校验 JVM 类型语法、void 位置、255 维数组和大小预算。`MemberDescriptor` 保存真实声明 owner、成员种类、名称、完整参数及返回/字段类型。`QuerySpec.exact` 为 portable 完整描述；`QuerySpec.builder(Class, kind)` 及 Class/实参便捷入口是 runtime-only，缓存按 Class 对象身份隔离，不将相同类名跨 loader 合并。实参入口只保存参数的 Class 或 null，不保留 receiver/对象值。

精确描述符先走公开精确反射入口。协变返回/bridge 的返回类型不符时，在预算内回退到声明域验证完整签名。类加载使用 `Class.forName(name, false, loader)`，查询不会主动初始化。不存在与系统过滤统一为 `UNAVAILABLE/not_found_or_filtered`，不会构造物理不存在证明。

`DECLARED` 只遍历声明类；`SUPERCLASSES` 按当前类、父类直到 Object 逐级处理；`JAVA_PUBLIC` 使用 Java 公共成员查询语义。构造器不继承，不提供自定义 interface DAG 查询。

新查询默认 `UNIQUE`：只有完整可见域中的单个匹配才成功；两个实际验证的不同成员可直接证明歧义。`FIRST` 保留公开反射枚举顺序，找到即停，`RESOLVED` 并不表示 coverage 完整或唯一。`ALL` 输出达到 limit 时保守标 `PARTIAL`。链接失败、预算、取消、Provider 不完整都不能提升成完整负结论。`requireSingle()` 只接受 `RESOLVED`，不会吞目标返回 null 或目标异常。

`CompatibilityPolicy.LEGACY_BIL` 使用严格装箱匹配，null 只接受引用类型；`JAVA_INVOCATION` 允许反射的 primitive widening，禁止 narrowing/boolean 混用。不实现 Java 编译器重载决议，不隐式打包 varargs。

结构筛选按 name/static/arity/flags 先短路，再建立一次参数快照。私有数组不会经 getter 外泄。精确查询不创建全类 schema；schema 缓存有界。query cache 只缓存完整且稳定的成功，默认没有负缓存。实际 metadata 计数不包含 VM 枚举内部分配、也不等于时间收益。

默认容量 4096，同时约束 query/schema 缓存和 live binding 数。query/schema 按 LRU 淘汰，已经发布的 binding 不自动驱逐；新成员超出 live binding 容量返回 `PARTIAL/binding_capacity`，既有计划仍有效。长期作用域调用方需选择合适容量或显式换作用域，不能把无界成员集合放入本库。

作用域 access policy 固定，默认不更改 accessible。显式 `TRY_SUPPRESS_JAVA_CHECKS` 只使用公共 `setAccessible`；实际拒绝以 IllegalAccessException 保留 cause，不承诺绕过 Java 模块或 Android hidden API。

MethodPlan、FieldPlan、ConstructorPlan 分离，无 receiver 字段。调用保留反射 conversion、virtual dispatch、初始化、InvocationTargetException；目标进入后不切候选或重试。计划只持 CodeScope 和描述 token，Member 存于 scope slot。原始 `exportRaw` 是显式所有权外逸，调用方可缓存或交给 Hook，关闭不会撤销已导出 Member。

`close`: OPEN→RETIRING→CLOSED。同步线性化点拒绝新调用并清理缓存、槽位及 loader；已进入的方法持自己的局部 Member，可完成后释放。没有无界等待。重复 close 幂等。Provider 在全部在途解析/调用排空后关闭；排空时的 Provider close 异常记录 `closeFailureType()`，不盖住目标异常。没有在途工作时显式 close 的关闭失败正常抛出。

`advanceCodeEpoch(reason)` 清除本进程绑定/缓存并使旧 token 失效；Long.MAX_VALUE 换新 scope identity，再从 0 计数。code epoch 变更同时使当前强代码指纹失效，Provider 需要携带新内容身份的新 scope。作用域不管理 UI 生命周期。

单线程调用不启动任何工作线程。single-flight 在同键 miss 上共享工作，等待最多 2000ms、并发 flight 上限 min(capacity,256)。同线程重入和跨线程等待环返回明确不完整结果；一个等待者中断不取消拥有者，close 完成等待者而不抢占已进入 Provider/目标。provider/目标代码不在全局锁内执行。


[返回文档目录](../README.md)
