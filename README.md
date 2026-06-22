[中文说明](README_zh.md)

# Lumine for Android

Lumine for Android is a personal proxy tool based on Android `VpnService`. It connects Android TUN traffic to Lumine core and provides a Compose UI for managing configs, rules, logs, and runtime status.

## Current Features

- Captures device traffic through Android `VpnService` and forwards it to the Lumine proxy adapter through the local `tun2socks` module.
- Keeps Lumine core's TCP/TLS handling, including policy modes such as `tls-rf`, `ttl-d`, and `direct`.
- Supports domain policies, IP policies, default policy, and rule editing.
- Supports per-app proxy selection, including selecting all search results, showing selected apps only, and clearing the selection.
- Supports config/subscription management, global settings, runtime logs, runtime statistics, and recent-event diagnostics.
- Supports upstream DNS configuration and sets timeouts for UDP DNS and DoH requests to avoid long DNS stalls.
- Uses canonical host:port target addresses for IPv6 targets on the Android TUN path, avoiding `too many colons in address` during TTL probing.
- Blocks QUIC by default to reduce traffic bypassing TCP/TLS policy handling.
- Provides a GitHub Actions build workflow that can generate AAR/APK artifacts in the cloud.

## Build AAR

```powershell
cd E:\testcode1\lumine\lumine-for-android-original
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
.\scripts\gomobile-bind.ps1 -Output android\app\libs\LumineCore.aar
```

You can also pass `-AndroidHome` and `-JavaHome` explicitly.

## Build APK

```powershell
cd E:\testcode1\lumine\lumine-for-android-original\android
.\gradlew.bat assembleDebug
```

Local builds require the Android SDK. If you do not have a local SDK, you can use GitHub Actions to build it. I do not have a local Android environment and was too lazy to install one, so I have used Actions for the whole process and have not tested the local build commands above.

## Tests

```powershell
go test -mod=mod ./...
```

## Acknowledgements

- Thanks to [moi-si/lumine](https://github.com/moi-si/lumine), the original project that provides the foundation of the Lumine core implementation.
- Thanks to [SniShaper/lumine-for-android](https://github.com/SniShaper/lumine-for-android) for the Android implementation inspiration.

## License

GPLv3
