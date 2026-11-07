# CleanYourTV — Minimal, Fast & Lightweight Android TV Launcher

CleanYourTV is a high-performance, ad-free, and lightweight launcher designed specifically for low-RAM Android TVs (such as 1 GB RAM Smart TVs and TV boxes). It delivers an instant, clutter-free home screen experience with zero telemetry, zero sponsored content, and a minimal memory footprint.

---

## ✨ Key Features

- **Low Memory Footprint:** Designed to keep idle memory low on Android 9+ TV hardware.
- **Zero Bloat & Zero Ads:** Clean, distraction-free interface focused purely on your apps and media.
- **Remote Handling:** Smooth long-press volume ramping by leaving volume keys unintercepted in the accessibility service (double-press Volume Down mute is handled by the TV firmware).
- **Custom Wallpapers & Dynamic Cycling:**
  - Automatic background wallpaper rotation.
  - Separate dedicated lockscreen wallpaper selection.
  - On-demand decoding in `RGB_565` to prevent memory leaks and cache bloat.
- **Integrated Title Search:** Single-click card navigation that launches search queries directly into media providers without dropping query parameters across activity lifecycles.
- **Sleep & Screen-Off Mode:** Screen blanking with hardware audio mute and wake-guard protections to prevent accidental input passthrough.
- **Accessibility Self-Repair:** Resilient background service monitoring to maintain Home key interception across unexpected system events.

---

## 📋 System Requirements

- **Platform:** Android TV 9.0 Pie (API level 28) or higher.
- **Architecture:** `armeabi-v7a`, `arm64-v8a`, `x86`, or `x86_64`.
- **RAM:** Minimum 1 GB total system memory.

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17
- Android SDK with Build Tools 34.0.0+ and Platform API 34+

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/zoro-uchiha-007/CleanYourTvMadeByZoro.git
   cd CleanYourTvMadeByZoro/launcher
   ```

2. Build the Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   The built APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. (Optional) Build the Release APK:
   Copy `keystore.properties.example` to `keystore.properties` and fill in your signing details:
   ```bash
   cp keystore.properties.example keystore.properties
   ./gradlew assembleRelease
   ```
   The signed release APK will be located at:
   `app/build/outputs/apk/release/app-release.apk`

---

## 🌤️ Weather Configuration (Optional)

By default, live weather fetching is disabled (`WEATHER_ENABLED = false`) and the weather indicator on the top bar is hidden cleanly.

To enable live local weather:
1. Open `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java`.
2. Locate the weather configuration block:
   ```java
   public static final boolean WEATHER_ENABLED = true;
   public static final double WEATHER_LATITUDE = 40.7128;   // Replace with your latitude
   public static final double WEATHER_LONGITUDE = -74.0060; // Replace with your longitude
   ```
3. Rebuild the application. Weather data is fetched from the free [Open-Meteo Weather API](https://open-meteo.com/) and cached locally for 30 minutes.

---

## 📲 Installation & TV Setup (via ADB)

### 1. Install the APK
Connect your computer to your TV over ADB and install:
```bash
adb connect <TV_IP_ADDRESS>:5555
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
```

### 2. Grant Required Permissions
CleanYourTV requires standard system permissions for brightness control and Wi-Fi status display:
```bash
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
```

### 3. Enable Accessibility Service (Home Key Interception)
To allow CleanYourTV to handle the TV remote's Home button seamlessly:
```bash
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
```

### 4. Setting CleanYourTV as the Default Launcher
To prevent stock launcher bloat and avoid launcher flashes when pressing Home, you can disable the stock TV launcher:
```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
# For Xiaomi / PatchWall TV models:
adb shell pm disable-user --user 0 com.mitv.tvhome.atv
```

> [!WARNING]
> **Safety Rollback Command:** If you ever need to restore your TV's original stock launcher, run:
> ```bash
> adb shell pm enable com.google.android.tvlauncher
> adb shell pm enable com.mitv.tvhome.atv
> ```

---

## 🎨 Customizing Wallpapers

Place your own 1280x720 JPEG images in `launcher/app/src/main/assets/wallpapers/`.
You can use the included resampling script to format images correctly:
```bash
python tools/resample_wallpapers.py /path/to/my_image.png launcher/app/src/main/assets/wallpapers/wallpaper_01.jpg
```

---

## ⚖️ Trademarks & Disclaimers

This is an independent open-source project. All product names, logos, brands, and registered trademarks mentioned in this repository are property of their respective owners. 

All company, product, and service names (including Google, Android, Xiaomi, Mi, Netflix, Amazon Prime Video, Disney, Hotstar, CloudStream, SmartTube, MX Player) are used strictly for identification and compatibility purposes only. Use of these names, logos, and brands does not imply endorsement, affiliation, or sponsorship by any of the respective trademark holders.

---

## 📄 License & Attributions

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
- Copyright (c) 2026 Gathin
- Copyright (c) 2026 zoro-uchiha-007

### Third-Party Components & Data Sources

- **Upstream Project:** [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT License).
- **Outfit Font:** Distributed under the SIL Open Font License 1.1 (OFL-1.1).
- **Microsoft Fluent Emoji:** Distributed under the MIT License by Microsoft Corporation.
- **Android Jetpack Libraries:** Distributed under the Apache 2.0 License by the Android Open Source Project.
- **TVMaze API:** Media recommendations and metadata provided by [TVmaze.com](https://www.tvmaze.com/) under the [Creative Commons Attribution-ShareAlike 4.0 International License (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/).
  > *"This product uses the TVmaze API but is not endorsed or certified by TVmaze."*
- **Kitsu API:** Anime trending titles provided via the [Kitsu API](https://kitsu.io/) under Kitsu Terms of Service.
  > *"Powered by Kitsu."*
- **Open-Meteo Weather API:** Weather forecasts provided by [Open-Meteo](https://open-meteo.com/) under the [Creative Commons Attribution 4.0 International License (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).
  > *"Weather data by Open-Meteo.com."*

For complete third-party notices, see [NOTICE](NOTICE).
