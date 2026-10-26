# 发布到 Maven Central

当前制品同时可从本仓库的 `maven` 分支获取。本文说明如何生成已签名的发布包并上传到 Maven Central。

> Central Portal 的规则会更新，操作前请对照官方文档：<https://central.sonatype.org/publish/requirements/>。

## Central 对制品的要求

- 每个模块有 `.pom`、`.jar`（或 `.aar`）、`-sources.jar`、`-javadoc.jar`。
- 每个文件有 GPG 签名 `.asc`，以及 `.md5`、`.sha1` 校验文件。
- POM 含 name、description、url、license、developer、scm。

构建已按此配置：加上 `-Pmeguviz.central=true` 后，会生成 javadoc 包、对发布内容签名，并输出到 `build/central-staging`。不加该参数时，日常构建与验证行为不变。

## 一次性准备

1. **命名空间**：登录 <https://central.sonatype.com>，在 Namespaces 中添加并验证 `io.github.jichuo1`（在 GitHub 创建以验证密钥命名的临时公开仓库）。
2. **GPG 密钥**：生成密钥，并把公钥上传到 `keyserver.ubuntu.com`。公钥上传后无法撤回，其中的邮箱会公开，建议使用 GitHub 的 noreply 邮箱。
3. **口令与私钥**不要提交到仓库。

## 生成发布包

```powershell
.\gradlew.bat '-Pmeguviz.profile=android' '-Pmeguviz.central=true' '-Psigning.gnupg.executable=<gpg 可执行文件路径>' '-Psigning.gnupg.keyName=<KEY_ID>' centralBundle
```

- 需要 `android` profile，才会包含 `meguviz-ref-android`。
- 签名时会弹出 Pinentry 窗口，输入 GPG 口令。
- 结果为 `build/central/meguviz-ref-<版本>-central.zip`，内含三个模块的全部文件、签名和校验文件。

## 上传与发布

1. 打开 <https://central.sonatype.com/publishing>，点击 Publish Component。
2. 填写 Deployment Name（如 `meguviz-ref-1.0.0`），选择上面生成的 zip 上传。
3. 等待 Portal 校验通过（签名、POM 元数据、校验和）。失败时按报错修复后重新生成并上传。
4. 校验通过后点击 Publish。通常几十分钟后可在 <https://repo1.maven.org/maven2/io/github/jichuo1/meguviz/> 查到。

**同一版本一旦发布到 Central，不能覆盖或删除。** 上传前确认版本号和内容无误。

## 发布后

- 保留 `maven` 分支作为备用来源。
- 验证：新建空项目，只配置 `mavenCentral()`，解析 `io.github.jichuo1.meguviz:meguviz-ref-core:<版本>`。
