# CableCast — Android (TV receiver + phone) — Build to .apk

One Gradle project, one adaptive app that installs on **both** Android TV and phones:
- On a TV it launches `TvActivity` (LEANBACK launcher) → the "ready to receive" screen.
- On a phone it launches `PhoneActivity` → the companion cast/remote screen.

## What you need
- **JDK 17**
- **Android SDK** (API 34) + build-tools. Easiest via **Android Studio** (Koala or newer),
  which also gives you an emulator / device deploy button.
- Internet access to `google()` + `mavenCentral()` (this environment can't reach them, so the
  `.apk` is built on your machine, not in the chat sandbox).

## Build the APKs

```bash
cd cablecast-android
# point Gradle at your SDK (or set ANDROID_HOME):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release-unsigned.apk
# or for a quick installable build:
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
```

### Sign the release APK (needed to install / publish)
```bash
keytool -genkey -v -keystore cablecast.jks -alias cablecast -keyalg RSA -keysize 2048 -validity 10000
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
  --ks cablecast.jks --out CableCast.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install
```bash
adb install -r CableCast.apk        # phone
adb install -r CableCast.apk        # Android TV box / stick (same APK)
```

## In Android Studio (simplest)
`File ▸ Open ▸ cablecast-android`, let it sync, then **Run ▸ app** on a TV emulator/device
and on a phone. Build ▸ Generate Signed Bundle / APK for the shippable `.apk`.

## Architecture (as implemented)
- **Transport:** WebRTC (H.264 + Opus), LAN-only (no STUN/TURN).
- **Signaling:** embedded WebSocket on the TV, **port 47800** (`SignalingServer` via NanoWSD).
  4-digit pairing code shown on the TV, verified before SDP exchange.
- **Discovery:** `NsdManager` advertises `_cablecast._tcp` for Wi-Fi auto-discovery;
  Ethernet mode connects straight by IP.
- **TV receiver:** `RtcReceiver` renders the incoming stream to a `SurfaceViewRenderer`.
- **Phone cast:** `RtcSender` + `MediaProjection` (`ProjectionService` foreground service).

## Dependency note
WebRTC comes from `io.github.webrtc-sdk:android` (a maintained prebuilt). If your org mirrors
Maven, make sure that artifact + `org.nanohttpd:nanohttpd-websocket` are reachable. Everything
else is standard AndroidX / Compose.

Built with Claude for Victor Gitu.
