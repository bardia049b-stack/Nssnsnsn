# Third party licences

NebulaBox itself is GPL-3.0 (see `LICENSE`). It is a client: the real work of the tunnel is done by
code written by other people, and this file lists it, where it comes from and what it asks for.

## Xray core

- Project: XTLS/Xray-core — <https://github.com/XTLS/Xray-core>
- Licence: Mozilla Public License 2.0
- Full text: <https://www.mozilla.org/en-US/MPL/2.0/>

The core is used as a library (`libv2ray.aar`), it is not modified by this repository.

## AndroidLibXrayLite (the Android build of the core)

- Project: 2dust/AndroidLibXrayLite — <https://github.com/2dust/AndroidLibXrayLite>
- Licence: GNU Lesser General Public License v3.0
- Full text: <https://www.gnu.org/licenses/lgpl-3.0.txt>

This project wraps the Xray core for Android and is what gives us `libv2ray.aar`. The library is
shipped unmodified: the workflow downloads the `.aar` of one pinned tag and only accepts it when its
SHA-256 matches. The library is dynamically linked by NebulaBox, which is what the LGPL asks for, and
the library itself may be replaced by anyone who builds it from its sources.

## hev-socks5-tunnel

- Project: heiher/hev-socks5-tunnel — <https://github.com/heiher/hev-socks5-tunnel>
- Licence: MIT
- Full text: <https://opensource.org/license/mit>

It carries the packets of the VPN interface to the local port of the core. The copy in this
repository is a build of the upstream project with the JNI entry point named after this app.

## AndroidX, Jetpack Compose, Material, Kotlin

The interface is built from the AndroidX and Jetpack Compose libraries and the Kotlin standard
library, all of which are published by Google and JetBrains under the Apache License 2.0
(<https://www.apache.org/licenses/LICENSE-2.0>). Full dependency metadata, including any transitive
licence, is available with:

```
./gradlew app:dependencies
```

## The geo data files

`geosite.dat` and `geoip.dat` inside the APK are build inputs for routing rules, produced by the
v2fly project from public sources, under the same Apache License 2.0
(<https://github.com/v2fly/domain-list-community>).

## A note on names

`com.v2ray.ang` is the package name of v2rayNG, the well known Android client for the same core. No
part of that application is compiled into this one; the package string only appears in this document
so that the origin of the core library is not left unclear.
