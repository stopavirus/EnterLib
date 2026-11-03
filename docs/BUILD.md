# Build
Requirements: JDK 17, Android SDK platform 34, Gradle 8.7 (or generate a wrapper: `gradle wrapper --gradle-version 8.7` and commit it).
```
gradle :core:test :catalog:test :data:testDebugUnitTest
gradle :app-mobile:assembleDebug :app-tv:assembleDebug
```
APKs: `app-mobile/build/outputs/apk/debug/`, `app-tv/build/outputs/apk/debug/`. CI uploads them as artifact **EnterLib-APKs**
(Actions → run → Artifacts). Install on TV with `adb connect <tv-ip>` then `adb install EnterLib-TV.apk`.

## Signed release (secure)
Create a keystore locally, base64 it into GitHub secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; in a release workflow
decode to a temp file and pass via Gradle properties to a `signingConfigs` block. Never commit keystores (they are git-ignored).
