# Stable DEBUG signing keystore

`app/debug.keystore` is a **public, committed, DEBUG-ONLY test signing key**. It exists for one
reason: to give every CI `assembleDebug` APK the **same** signing certificate, so a debug build
installed from one CI run can be updated **in place** by a debug build from a later run.

Android refuses to update an app whose new APK is signed by a different certificate. The Android
Gradle Plugin's default debug keystore (`~/.android/debug.keystore`) is generated with a **random
key on every fresh CI runner**, so every CI debug APK used to get a different certificate and
in-place updates failed with a package/signature conflict. Pinning the debug signing config to this
committed keystore fixes that for all future builds.

## Facts

| Field | Value |
| --- | --- |
| Path | `app/debug.keystore` |
| Type | JKS |
| Alias | `androiddebugkey` |
| Store password | `android` |
| Key password | `android` |
| Key | RSA 2048 |
| Subject | `CN=Tariffia Panel Debug, OU=Debug, O=Tariffia, C=US` |
| Certificate SHA-256 | `C7:3B:FD:C0:30:2A:C7:31:3B:0E:61:28:7E:53:B2:0B:F8:EA:6C:ED:A3:2B:5E:CD:38:27:62:F8:33:8F:6A:D2` |

## Rules

- **DEBUG/TEST ONLY.** This key must **never** be used to sign a production or release build.
  Release signing is supplied separately through CI secrets (`RELEASE_KEYSTORE_*`); that path is
  unchanged and independent of this file.
- The password is intentionally public because this is a throwaway debug identity; it protects
  nothing. Do not put real secrets here.
- `applicationId` stays `com.tariffia.panel`.
- The old APKs signed with a lost, ephemeral debug key cannot be updated in place — that is
  technically impossible. This keystore establishes the stable baseline for **future** APKs.
