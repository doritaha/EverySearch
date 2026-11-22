# Build and release guide for EverySearch (Android)

## Prerequisites
- JDK 17 and Android SDK 35
- `local.properties` with `sdk.dir=...`, which is machine-specific and not committed
- If Gradle fails with "Unable to establish loopback connection", set:
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\gtmp`

## Build
```bash
./gradlew assembleRelease   # or assembleDebug
```
The output is in `app/build/outputs/apk/release/`.

## Signing
The release APK must be signed with your own keystore. The keystore and its passwords are never committed. Pass them through `~/.gradle/gradle.properties` or environment variables.

## Publishing
1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Build and test on a real device, including root mode if you need it.
3. Create a GitHub Release with the tag `vX.Y` and attach the APK, named `EverySearch-vX.Y.apk`.
4. Push the code to both remotes:
   ```bash
   git push origin main --tags
   git push gitlab main --tags
   ```
