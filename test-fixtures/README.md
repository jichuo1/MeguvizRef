# 独立 JVM 夹具

`ref-core/src/test/java/io/github/jichuo1/meguviz/ref/CoreContractTest.java` 是无测试框架依赖的可执行合同套件，主入口接收 JUnit XML 路径。正式运行：

```powershell
.\gradlew.bat '-Pmeguviz.profile=host' :ref-core:contractTests
```

当前具名用例包括：完整类型描述符、协变 bridge、字段遮蔽、构造器、初始化、副作用/异常、实际双 loader、缺失签名类型、四类查询选择、primitive 64 格转换表、结构参数快照、关闭/代际竞争、Provider 声明复核和 single-flight 取消/重入/跨线程循环；准确执行数量以本次 XML 为准。

`isolated-src/fixture` 由运行测试的 JDK17 编译器编译并打包为 `ref-core/build/isolated-fixtures/complete-fixture.jar`。该 JAR 不加入测试进程 classpath，由两个 parent=null 的 URLClassLoader 分别加载。缺签名夹具 `missing-signature-fixture.jar` 只包含 MissingSignatureFixture.class，不提供 MissingType.class；JAR 条目时间固定为 0，输出仅写入构建目录。

期望来自手写完整描述符、独立 `Class.getDeclaredMethods` 结果或明确转换表。Provider 完整性回归刻意只返回一个存在的重载或空候选，确保其 `complete=true` 不能自证唯一/缺失。测试用 Provider 仅用于验证 MeguvizRef SPI。

GC 用例记录 `loaderCollected` 的实际观测，并验证关闭后的计划失效和库持有引用清理；不要求一次 GC 必定回收、不宣称即时类卸载。测试产物及源码摘要见 fixture-manifest.json 和实际 XML。
