# 上传到蒲公英（Upload to PGYER）

[English](README.md)

在 Jenkins 的 Pipeline 或自由风格任务中，把 Android（`.apk`）、iOS（`.ipa`）和鸿蒙（`.hap`）安装包上传到
[蒲公英](https://www.pgyer.com) 进行内测分发。本插件由蒲公英官方维护。

- 直接在构建应用的 agent 上上传，不会把安装包复制到 Jenkins 主节点。
- 等蒲公英发布完成后，返回下载页地址和二维码。
- API Key 保存在 Jenkins 凭据中，不会出现在构建日志里。
- 使用 Jenkins 的代理设置；`www.pgyer.com` 无法访问时自动改用蒲公英的备用域名。

需要 Jenkins 2.479.3 或更新版本。

## 准备

1. 在 <https://www.pgyer.com/account/api> 复制你的 API Key。
2. 在 Jenkins 中添加一个“Secret text”类型的凭据保存这个 Key，例如 ID 设为 `pgyer-api-key`。
3. 可选：在“系统管理 » 系统配置 » PGYER”中把它设为默认 Key，这样任务里就不用再指定。

## Pipeline

```groovy
pipeline {
    agent any
    stages {
        stage('构建') {
            steps {
                sh './gradlew assembleRelease'
            }
        }
        stage('分发') {
            steps {
                script {
                    def app = pgyerUpload file: 'app/build/outputs/apk/release/*.apk',
                                          credentialsId: 'pgyer-api-key',
                                          updateDescription: "Build ${env.BUILD_NUMBER}"
                    echo "下载页：${app.appPgyerURL}"
                    echo "二维码：${app.buildQRCodeURL}"
                }
            }
        }
    }
}
```

也可以通过环境变量 `PGYER_API_KEY` 提供 Key：

```groovy
withCredentials([string(credentialsId: 'pgyer-api-key', variable: 'PGYER_API_KEY')]) {
    pgyerUpload file: 'build/*.ipa'
}
```

### 参数

| 参数 | 说明 |
|---|---|
| `file` | 相对于工作空间的 Ant 风格路径模式。默认匹配任意 `.apk`、`.ipa` 或 `.hap` 文件；匹配到多个时上传最近修改的那个。 |
| `credentialsId` | 保存 API Key 的“Secret text”凭据。不填时依次使用 `PGYER_API_KEY` 环境变量、系统默认 Key。 |
| `buildType` | `android`、`ios` 或 `harmony`。只有文件扩展名不是 `.apk`、`.ipa`、`.hap` 时才需要。 |
| `installType` | `1` 公开安装，`2` 密码安装，`3` 邀请安装。不填时保持应用当前的设置。 |
| `password` | 安装密码，配合 `installType: '2'` 使用。 |
| `description` | 应用描述。 |
| `updateDescription` | 本次版本的更新说明。 |
| `channelShortcut` | 要发布到的渠道短链接。 |
| `installStartDate`、`installEndDate` | 安装有效期，格式 `yyyy-MM-dd`，两个要同时填写。 |
| `oversea` | `true` 使用海外加速上传，`false` 使用国内上传。不填时按 IP 自动判断。 |
| `processingTimeout` | 上传完成后等待蒲公英发布的最长时间（秒），默认 `600`。 |

### 返回值

`pgyerUpload` 返回一个 Map，包含蒲公英返回的全部版本信息，常用的有：

| 键 | 示例 |
|---|---|
| `buildName`、`buildVersion`、`buildBuildVersion` | `My App`、`1.2.0`、`7` |
| `buildKey`、`buildShortcutUrl` | `a1b2c3...`、`myapp` |
| `appPgyerURL` | `https://www.pgyer.com/myapp` |
| `buildQRCodeURL` | 二维码图片地址 |

构建页面上也会显示下载链接和二维码。

## 自由风格任务

添加“上传到蒲公英（PGYER）”构建步骤或构建后操作。作为构建后操作时，构建失败不会上传。

上传完成后，后续的构建步骤可以使用这些环境变量：`buildShortcutUrl`、`appPgyerURL`、`buildQRCodeURL`、
`appQRCodeURL`、`buildKey`、`buildName`、`buildVersion`，以及返回值中的其他字段。

把任务参数 `isUploadPgyer` 设为 `false` 可以跳过上传。

## 配置即代码（Configuration as Code）

```yaml
unclassified:
  pgyer:
    credentialsId: pgyer-api-key
```

## 从 2.x 升级

用 2.x 配置的任务无需修改即可继续使用，原来设置的环境变量名、读取的 `isUploadPgyer` 和 `uploadPgyerTimeout`
变量都保持不变。变化如下：

- 在 agent 上构建的任务可以正常上传了：现在会在 agent 上读取并上传文件。
- 插件最多等待 10 分钟让蒲公英发布版本，而不是原来的约 15 秒。
- 2.x 任务里直接填写的 API Key 仍然可用，但已标记为弃用。请改存为“Secret text”凭据，并在任务中选择它。
- Pipeline 任务请使用新的 `pgyerUpload` 步骤。

## 常见问题

| 错误 | 处理方法 |
|---|---|
| `1001`、`1002` | API Key 为空或不正确，请在 <https://www.pgyer.com/account/api> 核对。 |
| `1003` | 子账号不能使用 API，请使用主账号的 API Key。 |
| `1019` | 请先在 <https://www.pgyer.com/account/certification> 完成实名认证。 |
| `1020` | 该账号已被禁止上传，请联系蒲公英客服。 |
| `1022` | 账号存储空间已满或文件过大，请清理空间或升级套餐。 |
| `等待 600 秒后蒲公英仍在处理` | 上传已完成，但发布时间超过了 `processingTimeout`。版本之后可能仍会出现在蒲公英应用列表中；如果经常出现，可以调大等待时间。 |
| `所有蒲公英 API 服务器都无法连接` | agent 无法访问 `www.pgyer.com`、`www.xcxwo.com` 和 `www.pgyerapp.com`。请检查 agent 的网络，以及“系统管理 » 系统配置 » HTTP 代理配置”。 |

上传会使用 Jenkins 中配置的代理。通过需要账号密码的代理访问 HTTPS 时，Java 默认不发送 Basic 认证信息，需要在
agent 的 JVM 启动参数中加上 `-Djdk.http.auth.tunneling.disabledSchemes=""`。

## 问题反馈

请在 <https://github.com/jenkinsci/upload-pgyer-plugin/issues> 提交问题和建议。

## 许可证

[MIT](LICENSE)
