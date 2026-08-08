# 📱 ScreenTime — Parental Monitoring & Database Management System

A multi-module Android suite for real-time child device monitoring, parental screen time controls, and administrative database management backed by Firebase Firestore.

---

## 🏗️ Project Architecture

The workspace is structured into three independent Android modules:

```
ScreenTime/
├── 👶 app/       (Child Monitoring App)
├── 👨‍👩‍👧 parent/    (Parent Dashboard App)
└── 🛡️ admin/     (Admin Management Suite)
```

---

## ✨ Features by Module

### 👶 1. Child App (`:app`)
* **Foreground Session Tracking**: Captures continuous app usage intervals (`startTime` → `endTime`) via `AppUsageHelper` using `PACKAGE_USAGE_STATS`.
* **Background Monitoring Service**: Foreground service (`MonitorForegroundService`) reporting live foreground application, device battery, and online status to Firestore.
* **Telemetry Collection**: Monitors call logs, SMS messages, and notification alerts.
* **Auto-Start & Resilience**: Handles system reboots via `BOOT_COMPLETED` broadcast receiver.

### 👨‍👩‍👧 2. Parent Dashboard App (`:parent`)
* **Real-time Child Status**: Displays live online/offline indicator, current active application, and total daily screen time.
* **Usage Analytics**: Interactive bar chart displaying top app usage breakdown powered by `MPAndroidChart`.
* **Session Drill-down**: Clickable app details bottom sheet displaying exact session timelines (e.g., `09:15 AM – 09:42 AM, 27m`) grouped by date.
* **Activity Logs**: View-only tabs for Call Logs, SMS Messages, and Notification Alerts.

### 🛡️ 3. Admin App (`:admin`)
* **Full Firebase Database Control**: Complete admin dashboard to inspect, edit, or delete any stored document across families, children, app sessions, calls, messages, and notifications.
* **Digital Wellbeing Analytics**: Dedicated analytics dashboard with full date-wise usage bar charts spanning all historical data.
* **Per-Day Filtering**: Interactive chart tap-to-filter app usage per day.
* **Per-App Session Timeline**: Deep-dive bottom sheet (`AppSessionsBottomSheet`) for inspecting individual usage sessions across all dates.
* **Sleek Modern UI**: Custom dark mode interface with glassmorphic cards and chip badges.

---

## 🛠️ Tech Stack & Dependencies

* **Language**: Kotlin (JVM 11)
* **Target SDK**: 34 (Android 14) / Min SDK 26 (Android 8.0)
* **Backend**: Google Firebase Firestore, Authentication, Analytics
* **Charts**: [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) (v3.1.0)
* **UI**: Material Components, ViewBinding, ConstraintLayout, RecyclerView, BottomSheetDialog
* **Build System**: Gradle 8.x

---

## 🚀 Building & Running

### 1. Build APKs
Run the following command from the project root to compile all three APKs:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :admin:assembleDebug :parent:assembleDebug
```

### 2. Output APK Paths
* **Child App**: `app/build/outputs/apk/debug/app-debug.apk`
* **Admin App**: `admin/build/outputs/apk/debug/admin-debug.apk`
* **Parent App**: `parent/build/outputs/apk/debug/parent-debug.apk`

### 3. ADB Direct Installation
```bash
# Install Child App
adb install -r "app/build/outputs/apk/debug/app-debug.apk"

# Install Parent App
adb install -r "parent/build/outputs/apk/debug/parent-debug.apk"

# Install Admin App
adb install -r "admin/build/outputs/apk/debug/admin-debug.apk"
```

---

## 🔒 Samsung & Android 14 Installation Notes

If installing sideloaded APKs on Samsung devices (One UI 6 / Android 14), follow these steps to bypass installation blocks:

1. **Disable Auto Blocker**: `Settings` → `Security & Privacy` → `Auto Blocker` → **Turn OFF**.
2. **Allow Unknown Apps**: `Settings` → `Apps` → `⋮` → `Special App Access` → `Install Unknown Apps` → Select `My Files` → **ON**.
3. **Google Play Protect Warning**: If "App may be harmful" prompt appears, tap **"More Details"** → **"Install Anyway"**.

---

## 📄 License & Repository

Maintained by **Ajit ApTech**.
Repository: [github.com/Ajit-ApTech/ScreenTime](https://github.com/Ajit-ApTech/ScreenTime)
