# 发布到 Maven Central

当前制品只发布在本仓库的 `maven` 分支。本文说明如何把它们发布到 Maven Central，使使用者无需额外配置仓库地址。

> Central Portal 的规则会更新。操作前请对照官方文档：<https://central.sonatype.org/publish/publish-portal-gradle/>。

## 现状与差距

| 要求 | 现状 |
|---|---|
| 命名空间 `io.github.jichuo1` | 需在 Portal 验证 |
| POM：name、description、url、licenses、developers、scm | 已具备 |
| 源码包 `-sources.jar` | 已具备 |
| 文档包 `-javadoc.jar` | **缺失** |
| 所有文件的 GPG 签名 `.asc` | **缺失** |
| `.md5`、`.sha1` 校验文件 | 缺失（现有为 `.sha256`、`.sha512`；通常由发布插件生成） |

## 步骤

### 1. 注册并验证命名空间

1. 用 GitHub 账号登录 <https://central.sonatype.com>。
2. 在 Namespaces 中添加 `io.github.jichuo1`。
3. 按页面提示在 GitHub 账号下创建指定名称的临时公开仓库，完成验证后删除。

### 2. 生成发布令牌

在 Portal 的 Account 页生成 User Token，得到用户名与密码两段值。只放入 CI Secret 或本机 `~/.gradle/gradle.properties`，**不要提交到仓库**。

### 3. 准备 GPG 密钥

```bash
gpg --full-generate-key
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID>
```

- 公钥必须上传到公开密钥服务器，Central 校验签名时会查询。
- 私钥与口令只放入 Secret，不要提交。

### 4. 补齐构建配置

在 `ref-core`、`ref-diagnostics`、`ref-android` 中：

- 在 `java { … }` 加入 `withJavadocJar()`；Android 模块在 `singleVariant("release")` 中加入 `withJavadocJar()`。
- 应用 `signing` 插件，对 `publishing.publications` 签名，密钥从环境变量或 Gradle 属性读取。
- 引入支持 Central Portal 的发布插件（如 `com.vanniktech.maven.publish` 或 `com.gradleup.nmcp`），由它生成校验文件并上传。

### 5. 先发布为草稿并校验

1. 执行插件提供的 Portal 上传任务；选择"手动发布"模式，上传后停在 Portal 的 Deployments 页。
2. 等待 Portal 校验通过（签名、POM 元数据、校验和、文件完整性）。
3. 校验失败时按报错修复，**同一版本号在 Central 一旦发布不可覆盖或删除**，所以先在草稿阶段确认无误。

### 6. 发布

在 Deployments 页点击 Publish。通常几十分钟内可在 <https://repo1.maven.org/maven2/io/github/jichuo1/meguviz/> 查到，搜索索引更新会稍晚。

### 7. 发布后

- 把 README 与 [安装说明](installation.md) 中的"未发布到 Maven Central"改为可直接使用 `mavenCentral()`，并保留 `maven` 分支仓库作为备用来源。
- 验证：新建空项目，只配置 `mavenCentral()`，解析 `io.github.jichuo1.meguviz:meguviz-ref-core:1.0.0`。

## 注意

- 已发布到 `maven` 分支的 `1.0.0` 与上传到 Central 的 `1.0.0` 内容必须一致，否则同一坐标会出现两份不同二进制。建议用现有制品的 SHA-256 与新构建结果比对；如不一致，改发 `1.0.1` 到 Central。
- 加入 javadoc 包与签名会改变发布内容，发布前重新跑完整验证。
