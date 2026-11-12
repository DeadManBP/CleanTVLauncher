# Clean TV

An apps-only Android TV launcher. A grid of your installed apps, a clock, and a
Settings tile. No rows, no recommendations, no ads, no network use at all.

Built for the Philips 65PFL5704/F7 A (Android 11 Google TV), but it works on any
Android TV / Google TV device, API 21+.

## What it is

- **v1:** app grid (Leanback + regular launchers, sorted A–Z), live clock,
  Settings tile, blood-red focus ring, black background.
- **What it is not:** no recommendations, no Watch Next, no voice search, no
  accounts, no tracking. It does not change your apps — it only replaces the
  home screen.

## Install

Every push to `main` builds a debug APK in GitHub Actions
(`CleanTVLauncher-debug` artifact).

From a PC on the same Wi-Fi as the TV (or the Steam Deck in Desktop Mode):

```
adb connect <TV-IP>:5555
adb install -r app-debug.apk
```

## Set it as the home screen

Press **Home** on the remote and pick **Clean TV** when the TV asks which home
app to use. If it does not ask, force it:

```
adb shell cmd package set-home-activity "ca.cleantvlauncher/.MainActivity"
```

## Go back

- Pressing Home and picking the Google TV launcher, or:
- `adb shell cmd package set-home-activity "com.google.android.tvlauncher/.MainActivity"`
- Or uninstall: `adb uninstall ca.cleantvlauncher` — the stock launcher comes back.

## Build locally

Needs JDK 17 + Android SDK 34. No Gradle wrapper is shipped; use a system
Gradle 8.7:

```
gradle assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.


## CleanTV 2.0 development

CleanTV 2.0 is being developed in the `cleantv-2.0` branch as a separate Android application module (`app2`). Its debug test build uses the package ID `ca.cleantvlauncher2.dev`, so it can be installed alongside the existing Clean TV launcher without replacing it. The dedicated GitHub Actions workflow builds only `:app2:assembleDebug` and publishes a downloadable test APK artifact. The original `app` module and `main` branch remain untouched.
