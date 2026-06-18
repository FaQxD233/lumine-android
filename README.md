# lumine for Android, original-core edition

This project is an Android/TUN wrapper around the original `lumine@v0.9.0` core.

It intentionally does not use the `enimul-main` rewrite and does not import the Android fork's core changes such as Fake IP routing, GFWlist routing, recorded DNS answer routing, or the rewritten policy/config engine.

## What Is Included

- Original `lumine@v0.9.0` core files under `internal/`.
- Android `VpnService` shell and Compose UI adapted from `lumine-for-android-main`.
- `gomobile` binding package under `mobile/`.
- Local `tun2socks` module for forwarding Android TUN traffic into the Lumine proxy adapter.
- A small adapter layer:
  - `internal/dial_plan.go`
  - `internal/tun_bridge.go`
  - `internal/dns_mobile.go`

## Important Limits

The original Lumine is a SOCKS5/HTTP explicit proxy. Those protocols can carry the target domain name.

Android TUN traffic normally carries only destination IP addresses. Because this edition does not add Fake IP or DNS-record routing, domain-policy behavior is necessarily more limited than `lumine-for-android-main`:

- IP policies work naturally.
- TCP TLS traffic can still apply TLS fragmentation/desync when the first ClientHello is seen.
- SNI can refine the policy for the first TLS record, but this edition does not reconnect to a different `host` target after discovering SNI.
- DNS hijack is pass-through to the configured upstream DNS, not Fake IP synthesis.

This is the intended tradeoff: original core first, Android support second.

## Build Go AAR

```powershell
cd E:\testcode1\lumine\lumine-for-android-original
.\scripts\gomobile-bind.ps1 -AndroidHome D:\Android\Sdk -JavaHome D:\Android\jbr -Output android\app\libs\LumineCore.aar
```

Adjust SDK/JBR paths for your machine.

## Build APK

```powershell
cd E:\testcode1\lumine\lumine-for-android-original\android
.\gradlew.bat assembleDebug
```

## Verification

```powershell
go test -mod=mod ./...
```
