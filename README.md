# reNavigator 🚗📍

**reNavigator** is a lightweight, distraction-free native Android companion app for drivers. It continuously listens in the background for incoming navigation triggers (e.g. `#nav`) across chat apps (**WhatsApp, Telegram, Messenger, Signal, SMS**), parses coordinates, addresses, or map links, and presents an interactive **1-tap Floating HUD Action Card** directly over **Waze** or **Google Maps**.

---

## 🌟 Features

- **👂 Smart Notification Listener**: Automatically detects messages containing `#nav` (or customizable triggers) in real time from passengers or dispatchers.
- **🌐 Universal Map URL Resolver**:
  - Unpacks Google Maps short redirect links (`https://maps.app.goo.gl/...`).
  - Resolves Waze links (`waze.com/ul?ll=...`).
  - Resolves Apple Maps, OpenStreetMap (`osm.org`), and `geo:` URIs.
- **🎯 Universal Coordinate Parsing**:
  - Standard Decimal: `56.9496, 24.1052`
  - European Comma Notation: `56,9496, 24,1052` or `56,9496 24,1052`
  - Cardinal Formats: `N56.9496, E24.1052` or `56.9496N 24.1052E`
  - Degrees, Minutes, Seconds (DMS): `56°56'58.6"N 24°06'18.7"E`
- **🔎 Smart Place Name & Venue Geocoder**:
  - Free-text queries (e.g. `#nav Sigulda` or `#nav PetCity Ķīšezers Rimi`).
  - Proximity bias towards the driver's current GPS location.
  - Unicode diacritics transliteration (`Ķīšezers` $\to$ `Kisezers`) for 100% geocoding reliability.
- **🪟 2-Row Floating HUD Action Card**:
  - Floats cleanly on top of Waze without obstructing route guidance.
  - **Row 1**: Compass icon + single-line place name + `[ ✕ ]` close button.
  - **Row 2**: Real-time distance (`📍 14.8 km away`) + prominent **`[ 🚗 GO Waze ]`** button.
  - Touch-draggable across the screen.
- **🛡️ 1-Tap Immediate Navigation Trampoline**: Bypasses Android Background Activity Launch (BAL) restrictions to launch Waze and start turn-by-turn routing immediately.
- **📜 History & Sandbox**: Review received destination history or simulate incoming `#nav` alerts directly inside the app.

---

## 🔒 Permission Requirements & Usage

reNavigator requires a few specialized Android permissions to operate seamlessly while driving. Here is what each permission is used for:

| Permission | Android Name | Why It Is Needed |
| :--- | :--- | :--- |
| **Notification Listener** | `BIND_NOTIFICATION_LISTENER_SERVICE` | Used exclusively to read incoming messages containing `#nav` from chat apps so the driver doesn't need to manually copy-paste or switch apps while driving. |
| **Display Over Other Apps** | `SYSTEM_ALERT_WINDOW` | Used to draw the interactive 2-row Floating Action Card over Waze/Maps. |
| **Location Access** | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Used to calculate the distance to the destination (e.g. `14.8 km away`) and to bias place search results to nearby locations. |
| **Post Notifications** | `POST_NOTIFICATIONS` | Used to display high-priority Heads-Up alerts with direct `[ GO (Waze) ]` and `[ GO (Maps) ]` action buttons in the Android notification shade. |
| **Foreground Service** | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the background service reliably active during multi-hour road trips without being killed by Android battery optimization. |
| **Internet Access** | `INTERNET` | Used to resolve HTTP redirects for shortened map URLs (e.g. `maps.app.goo.gl`) and geocode venue/place names. |

---

## 📱 Installation & Setup

### 1. Build or Install the APK
Download the debug build or build from source using Gradle:
```bash
./gradlew assembleDebug
```
The generated APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Initial Configuration (One-Time)
1. Open **reNavigator**.
2. **Grant Notification Access**:
   * Tap **"Grant Permission"** on the Notification card.
   * Toggle **reNavigator** to **Allowed**.
   * *(Note on Android 13/14/15: If Android shows "Restricted Setting", go to **App Info > 3 dots (top right) > Allow restricted settings**, then grant Notification Access).*
3. **Enable Floating Action Bubble**:
   * Toggle the **Floating Action Bubble** switch.
   * Grant **"Allow display over other apps"** in Android Settings.
4. **Choose Preferred Navigation App**:
   * Select **Waze** or **Google Maps** as your default 1-tap navigation target.

---

## 🧪 Testing with the Sandbox
You can verify full functionality without waiting for a real message:
1. Navigate to the **Sandbox** tab in reNavigator.
2. Tap any preset (e.g. **Google Short Link**, **Sigulda**, or **Comma Decimals**).
3. Tap **"Simulate Heads-Up Nav Alert"**.
4. Switch to Waze—the 2-row HUD card will appear over Waze with distance and the **`[ 🚗 GO Waze ]`** button.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose & Material 3
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Network**: OkHttp3 for redirect following & Geocoder REST API
- **Persistence**: Jetpack DataStore (Preferences) & Room (History items)
- **Background Architecture**:
  - `ReNavNotificationListenerService` (Notification extraction & GPS bias)
  - `FloatingOverlayService` (`WindowManager` HUD overlay)
  - `NavigationTrampolineActivity` (Direct Intent dispatch to Waze / Maps)

---

## 📄 License
MIT License. Free for personal and commercial driving use.
