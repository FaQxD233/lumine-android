# Lumine for Android

Lumine for Android 是一个基于 Android `VpnService` 的自用代理工具，把 Android TUN 流量接入 Lumine core，并提供 Compose 图形界面管理配置、规则、日志和运行状态。

## 当前功能

- Android `VpnService` 接管设备流量，并通过本地 `tun2socks` 转发到 Lumine 代理适配层。
- 保留 Lumine core 的 TCP/TLS 处理能力，包括 `tls-rf`、`ttl-d`、`direct` 等策略模式。
- 支持域名策略、IP 策略、默认策略和规则编辑。
- 支持分应用代理选择，可以全选搜索结果、只看已选应用、清空选择。
- 支持配置/订阅管理、全局设置、运行日志、运行统计和最近事件诊断。
- 支持 DNS 上游配置，并为 UDP DNS 与 DoH 请求设置超时，避免 DNS 请求长期卡住。
- Android TUN 路径下处理 IPv6 目标地址时使用带端口的规范地址格式，避免 TTL 探测遇到 IPv6 时报 `too many colons in address`。
- 默认阻断 QUIC，减少流量绕过 TCP/TLS 策略处理的情况。
- 提供 GitHub Actions 构建流程，可在云端生成 AAR/APK。

## 构建 AAR

```powershell
cd E:\testcode1\lumine\lumine-for-android-original
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
.\scripts\gomobile-bind.ps1 -Output android\app\libs\LumineCore.aar
```

也可以显式传入 `-AndroidHome` 和 `-JavaHome`。

## 构建 APK

```powershell
cd E:\testcode1\lumine\lumine-for-android-original\android
.\gradlew.bat assembleDebug
```

本地构建需要 Android SDK。没有本地 SDK 时，可以使用 GitHub Actions 构建。不过我没本地懒得下环境，所以全程都用的 actions，没测试过上面的编译方式。

## 测试

```powershell
go test -mod=mod ./...
```

## 鸣谢

- 感谢 [moi-si/lumine](https://github.com/moi-si/lumine) 原项目提供 Lumine core 的实现基础。
- 感谢 [SniShaper/lumine-for-android](https://github.com/SniShaper/lumine-for-android) 提供 Android 版实现的灵感来源。

## 许可

GPLv3
