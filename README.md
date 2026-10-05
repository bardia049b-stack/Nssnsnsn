# NebulaBox / JavidTun

NebulaBox is the name of the app, JavidTun is the name it is released under. It is a client for the
Xray core: it takes a share link or a subscription, keeps the servers in one list, tells you how much
of the subscription is left, and sends the traffic of the phone through the one you pick.

Persian is translated from the ground up, the interface follows the system language, and the whole
thing is built to stay small.

## What it does

- Servers from a share link, a clipboard paste, a QR image or the camera, or a subscription URL
- One screen with the list, one button to connect, one place for the settings
- A quota and expiry bar that comes from the subscription itself, and a clear state when the
  subscription runs out of traffic instead of a silent failure
- Routing with per app rules, fragment, mux, DNS and the rest of the technical side kept under
  `Advanced` in the settings
- A small update checker that reads this repository's releases and verifies the download
- An update is offered, never forced: you press it, it happens

## Download

Every release carries one APK per architecture. Pick the one for your phone:

| file | for |
| --- | --- |
| `...-fdroid_arm64-v8a.apk` | most phones of the last years |
| `...-fdroid_armeabi-v7a.apk` | older 32-bit phones |
| `...-fdroid_x86_64.apk` | emulators and tablets |
| `...-fdroid_x86.apk` | old emulators |

Each APK has a detached signature (`.apk.sig`) and there is a public key (`JavidTun-public-key.asc`)
plus a hash list (`SHA256SUMS.txt`) in the same release:

```
gpg --import JavidTun-public-key.asc
gpg --verify JavidTun-....apk.sig JavidTun-....apk
sha256sum -c SHA256SUMS.txt
```

## Versions

The version lives in `gradle.properties` and is bumped there, not by the workflow:

```
APP_VERSION_NAME=2.1.0
APP_VERSION_CODE=43
```

Every architecture gets its own install number, so two files of the same release can never look like
the same app to the installer: armeabi-v7a is `431`, arm64-v8a `432`, x86 `433`, x86_64 `434`.
Release builds up to `2.1.0-42` used the plain number of the workflow run (`42`), so the numbers of
this release are larger than every older build and the update installs over it as usual.

## Build

```
git clone https://github.com/bardia049b-stack/Nssnsnsn
cd Nssnsnsn
./gradlew assembleFdroidRelease
```

The Xray core arrives as `app/libs/libv2ray.aar`, built by the AndroidLibXrayLite project. The
workflow fetches it at a pinned tag, checks its hash, and never trusts a download that does not
match.

## Licence

GPL-3.0, see `LICENSE`. The parts that come from other people keep their own licences, they are
listed with their texts in `THIRD_PARTY_LICENSES.md`.
