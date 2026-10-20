# Upload to PGYER

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/upload-pgyer.svg)](https://plugins.jenkins.io/upload-pgyer)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/upload-pgyer.svg?color=blue)](https://plugins.jenkins.io/upload-pgyer)

[中文文档](README_cn.md)

Upload Android (`.apk`), iOS (`.ipa`) and HarmonyOS (`.hap`) builds to [PGYER](https://www.pgyer.com) for beta
distribution, from Pipeline or Freestyle jobs. Maintained by PGYER.

- Uploads from the agent that built the app; nothing is copied to the controller.
- Waits until PGYER has published the build, then returns its download page and QR code.
- Keeps the API key in Jenkins credentials and out of the build log.
- Uses Jenkins' proxy settings, and falls back to PGYER's backup domains when `www.pgyer.com` is unreachable.

Requires Jenkins 2.479.3 or newer.

## Set up

1. Copy your API key from <https://www.pgyer.com/account/api>.
2. In Jenkins, add a **Secret text** credential holding the key, for example with the ID `pgyer-api-key`.
3. Optionally, choose it as the default under **Manage Jenkins » System » PGYER**, so jobs don't have to name it.

## Pipeline

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh './gradlew assembleRelease'
            }
        }
        stage('Distribute') {
            steps {
                script {
                    def app = pgyerUpload file: 'app/build/outputs/apk/release/*.apk',
                                          credentialsId: 'pgyer-api-key',
                                          updateDescription: "Build ${env.BUILD_NUMBER}"
                    echo "Download page: ${app.appPgyerURL}"
                    echo "QR code: ${app.buildQRCodeURL}"
                }
            }
        }
    }
}
```

The key can also come from the `PGYER_API_KEY` environment variable:

```groovy
withCredentials([string(credentialsId: 'pgyer-api-key', variable: 'PGYER_API_KEY')]) {
    pgyerUpload file: 'build/*.ipa'
}
```

### Parameters

| Parameter | Description |
|---|---|
| `file` | Ant-style pattern relative to the workspace. Defaults to any `.apk`, `.ipa` or `.hap` file. If several match, the most recently modified one is uploaded. |
| `credentialsId` | Secret text credential with the API key. Defaults to `PGYER_API_KEY`, then to the global default. |
| `buildType` | `android`, `ios` or `harmony`. Only needed when the file extension is not `.apk`, `.ipa` or `.hap`. |
| `installType` | `1` public, `2` password, `3` invitation only. Keeps the current setting of the app when not set. |
| `password` | Install password, used with `installType: '2'`. |
| `description` | Description of the app. |
| `updateDescription` | What changed in this build. |
| `channelShortcut` | Short URL of the download channel to publish to. |
| `installStartDate`, `installEndDate` | Limit installs to a date range, as `yyyy-MM-dd`. Set both or neither. |
| `oversea` | `true` to upload through overseas acceleration, `false` for mainland China. Chosen by IP address when not set. |
| `processingTimeout` | Seconds to wait for PGYER to publish the build after the upload. Default `600`. |

### Return value

`pgyerUpload` returns a map with everything PGYER reports about the published build, including:

| Key | Example |
|---|---|
| `buildName`, `buildVersion`, `buildBuildVersion` | `My App`, `1.2.0`, `7` |
| `buildKey`, `buildShortcutUrl` | `a1b2c3...`, `myapp` |
| `appPgyerURL` | `https://www.pgyer.com/myapp` |
| `buildQRCodeURL` | URL of the QR code image |

The build page also shows the download link and QR code.

## Freestyle jobs

Add the **Upload to PGYER** build step or post-build action. As a post-build action it doesn't upload when the build
failed.

After the upload, later build steps can use these environment variables: `buildShortcutUrl`, `appPgyerURL`,
`buildQRCodeURL`, `appQRCodeURL`, `buildKey`, `buildName`, `buildVersion` and the other fields of the return value.

Set the job parameter `isUploadPgyer` to `false` to skip the upload.

## Configuration as Code

```yaml
unclassified:
  pgyer:
    credentialsId: pgyer-api-key
```

## Upgrading from 2.x

Jobs configured with 2.x keep working without changes, including the environment variables they set and the
`isUploadPgyer` and `uploadPgyerTimeout` variables they read. What changes:

- Builds on agents work: the file is now read and uploaded on the agent.
- The plugin waits up to 10 minutes for PGYER to publish the build, instead of about 15 seconds.
- The API key typed into a 2.x job is still used, but marked as deprecated. Move it to a Secret text credential
  and choose that credential in the job.
- Pipeline jobs use the new `pgyerUpload` step.

## Troubleshooting

| Error | What to do |
|---|---|
| `1001`, `1002` | The API key is missing or wrong. Check it at <https://www.pgyer.com/account/api>. |
| `1003` | Sub-accounts can't use the API. Use the API key of the main account. |
| `1019` | Complete real-name verification at <https://www.pgyer.com/account/certification>. |
| `1020` | Uploading is disabled for this account. Contact PGYER customer service. |
| `1022` | The account storage is full or the file is too large. Free up space or upgrade your plan. |
| `still processing the build after 600 seconds` | The upload finished but publishing took longer than `processingTimeout`. The build may still appear in your PGYER app list; raise the timeout if this happens often. |
| `None of the PGYER API servers could be reached` | The agent can't reach `www.pgyer.com`, `www.xcxwo.com` or `www.pgyerapp.com`. Check its network and the proxy under **Manage Jenkins » System » HTTP Proxy Configuration**. |

The upload uses the proxy configured in Jenkins. Java doesn't send Basic credentials when tunnelling HTTPS through a
proxy unless the agent JVM is started with `-Djdk.http.auth.tunneling.disabledSchemes=""`.

## Reporting issues

Report bugs and feature requests at <https://github.com/jenkinsci/upload-pgyer-plugin/issues>.

## License

[MIT](LICENSE)
