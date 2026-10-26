# 解析证据与代码身份

`ResolutionReceipt` 是无运行对象的值记录：查询摘要、选择模式、结果状态、可见域、检查数量、完整性、描述符、reason与异常类型名。它不保存Throwable、Class、Member、receiver、loader或进程地址。运行scope token与portable receipt分离。

`CodeIdentity.Strength` 区分STRONG、WEAK、UNVERIFIED。strong factory要求每个内容摘要是64位小写SHA-256，但不替调用方读文件；`CodeIdentities.hashFiles` 为可选实际文件hasher。文件mtime和版本号只能构造weak提示，不能命中持久化可信恢复。

独立复验器不调用核心选择器：使用公开反射枚举，独立形成primitive/array/object描述并匹配owner、完整参数/返回与flags。签名缺类或公开API不可用产生UNAVAILABLE；不将未完成枚举归类成不存在。

实际测试见 ref-diagnostics/src/test/java 下 DiagnosticsContractTest；运行回执在 verification/commands 与 ref-diagnostics/build/test-results/contracts。
