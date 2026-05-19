# SoraAdhya Android App

Native Android wrapper for [soraadhya.netlify.app](https://soraadhya.netlify.app/).

## Features

- WebView-based shell that loads the live site
- Swipe-to-refresh
- Hardware back button navigates the page history first
- Offline detection with retry screen
- External links (non `soraadhya.netlify.app`) open in the user's browser
- Material 3 Day/Night theme, adaptive launcher icon
- Min SDK 26, Target SDK 34

## Project layout

```
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/soraadhya/app/MainActivity.kt
    res/
      layout/activity_main.xml
      values/{strings,colors,themes,ic_launcher_background}.xml
      drawable/ic_launcher_foreground.xml
      mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml
      xml/backup_rules.xml
build.gradle.kts
settings.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.properties
```

## Build

Requirements:
- Android Studio Hedgehog (2023.1+) or any IDE with Android Gradle Plugin 8.5+
- JDK 17
- Android SDK with platform 34 installed

From the project root:

```bash
# generate the gradle wrapper jar/scripts (once)
gradle wrapper

# debug build
./gradlew :app:assembleDebug

# release build (unsigned)
./gradlew :app:assembleRelease
```

Resulting APK: `app/build/outputs/apk/debug/app-debug.apk`

Alternatively, open the project in Android Studio and use **Run > Run 'app'**.

## Getting the APK without a local toolchain

A GitHub Actions workflow (`.github/workflows/android.yml`) builds a debug APK
on every push. To grab it:

1. Push to GitHub (or open the repo on github.com).
2. Open the **Actions** tab and click the latest **Android CI** run on this
   branch.
3. Scroll to **Artifacts** and download **SoraAdhya-debug-apk**.
4. Unzip; install `SoraAdhya-debug.apk` on your device (enable "Install
   unknown apps" for your file manager / browser if prompted).

You can also trigger a build manually from the Actions tab via **Run workflow**.

## Customizing the target URL

The site URL lives in `MainActivity.kt` as `homeUrl`. Update it there if the
deployment URL changes.
