# 📱 Smart Battery Monitor — Android App (V1.0)

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](#)
[![Build](https://img.shields.io/badge/build-Stable-blue.svg)](#)
[![UI](https://img.shields.io/badge/UI-Material%20Design%203-orange.svg)](#)
[![Language](https://img.shields.io/badge/Kotlin-100%25-purple.svg)](#)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

A **modern Android companion app** for the **[Smart Battery Monitor ESP8266](https://github.com/akshit-singhh/Smart-Battery-Monitor-ESP8266-V1.0)** project.  
It connects over **local Wi-Fi (LAN or AP mode)** — no cloud required — to monitor battery health, status, and diagnostics in real time.

The app provides a **live dashboard**, **QR code Wi-Fi setup**, **signal strength tracking**, and **REST API integration** for effortless data visualization and configuration.

<p align="center">
  <img src="https://github.com/user-attachments/assets/aa8a7549-5dc3-4123-a252-526b7da6f483" width="220"/>
</p>

---
## ⚡ Requires Smart Battery Monitor ESP8266 Firmware

This app is designed to work with the [Smart Battery Monitor ESP8266](https://github.com/akshit-singhh/Smart-Battery-Monitor-ESP8266-V1.0) project.

Make sure the ESP8266 firmware is flashed and running before using the app.

# 📱 App UI Preview

<p align="center">
  <img src="https://github.com/user-attachments/assets/12fd5b27-a673-4db3-9928-9dfcd2ea2418" width="220"/>
  <img src="https://github.com/user-attachments/assets/97e81967-3417-4224-afdb-b7365867a04e" width="220"/>
  <img src="https://github.com/user-attachments/assets/f33544d2-f69d-4d3a-88c2-f17c073e3593" width="220"/>
  <img src="https://github.com/user-attachments/assets/816185ab-0375-4556-9633-e1d1fd1bc6be" width="220"/>
</p>

## ✨ App Features

| Feature | Description |
|----------|--------------|
| 📶 **Wi-Fi Auto Scan** | Detects ESP8266 device IP dynamically within your LAN subnet. |
| 📸 **QR-Based Setup** | Uses **ML Kit + CameraX** to scan QR code and auto-connect. |
| ⚡ **Live Dashboard** | Real-time display of Voltage, Current, Power, SOC, and Status. |
| 🛰️ **Connection Modes** | Supports both **AP mode** (direct) and **STA mode** (router-based). |
| 📊 **Signal Strength (RSSI)** | Live Wi-Fi signal level displayed on dashboard. |
| 🧾 **Serial Log Viewer** | View ESP8266 serial logs for debugging and validation. |
| 🔁 **Auto Refresh** | Refreshes live data every 5 seconds. |
| ⚙️ **Settings Sync** | Adjust calibration and battery configuration from app. |

---

## 🧠 Tech Stack

**Language:** Kotlin  
**Architecture:** MVVM (ViewModel + LiveData)  
**UI Framework:** Material Design 3  

**Libraries Used:**
- `Retrofit2` + `Gson` → RESTful API Communication  
- `OkHttp3` → HTTP Client + Logging  
- `Coroutines` → Asynchronous network calls  
- `CameraX` + `MLKit` → QR Code Scanner  
- `ConstraintLayout` + `Material Components` → UI Design  
- `Coil` → Image Loading (SVG & PNG support)

---

## 📦 App Setup Guide

Follow these 4 simple steps to build and run the app:

### ✅ Step 1 — Project Configuration

- Open the project in **Android Studio Flamingo or newer**  
- Minimum SDK: **24 (Android 7.0)**  
- Target SDK: **34**  
- Language: **Kotlin**

### ✅ Step 2 — Install Required Dependencies

In your **app-level `build.gradle`**, make sure these libraries are included:

```gradle
// Material UI
implementation("com.google.android.material:material:1.12.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// QR Code Scanner
implementation("com.google.mlkit:barcode-scanning:17.3.0")
implementation("androidx.camera:camera-core:1.3.4")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")

// Coil Image Loading
implementation("io.coil-kt:coil:2.6.0")
implementation("io.coil-kt:coil-svg:2.6.0")

```
💡 Tip: Sync your Gradle project after adding dependencies.

### ✅ Step 3 — App Permissions

Add these permissions in your AndroidManifest.xml:
```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

### ✅ Step 4 — Enable Cleartext Communication (for ESP HTTP)

Create this file:
📁 app/src/main/res/xml/network_security_config.xml
```
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.1.99</domain>
        <domain includeSubdomains="true">192.168.4.1</domain>
    </domain-config>
</network-security-config>
```
Attach it to your AndroidManifest.xml inside <application> tag:
```
android:networkSecurityConfig="@xml/network_security_config"
```
✅ Fixes:
UnknownServiceException: CLEARTEXT communication not permitted

---
## 🌐 REST API Integration
The app communicates directly with the **ESP8266 firmware** using the following endpoints:

| **Endpoint**      | **Description** |
|--------------------|-----------------|
| `/live_data`       | Returns real-time voltage, current, SOC, power, RSSI, and Wi-Fi mode |
| `/serial_log`      | Fetches system logs for diagnostics |
| `/wifi_config`     | `POST` Wi-Fi credentials for setup |
| `/settings`        | Read/update calibration & SOC settings |

### 📊 Example /live_data Response
```
{
  "voltage": 12.45,
  "current": 1.25,
  "power": 15.56,
  "soc": 86.7,
  "status": "Charging",
  "rssi": -58,
  "mode": "STA",
  "ip": "192.168.1.102"
}
```
# ⚙️ How It Works

- ESP8266 runs in either **AP mode** or **STA mode**.
- The Android app scans the local network for ESP IP.
- Once connected, it fetches `/live_data` JSON every 5 seconds.
- User can view **Voltage**, **Current**, **Power**, **SOC**, and **RSSI**.
- `/serial_log` tab shows real-time serial debug data.
- `/wifi_config` and `/settings` enable configuration updates.

---

# 🚀 Future Improvements

- 📈 Graph View for historical data
- ☁️ Optional Cloud Sync (Blynk / Firebase / Home Assistant)
- 🔔 Push notifications for low battery alerts
- 🌙 Dark Mode support
- 🧠 AI-based SOC prediction

---

# 👨‍💻 Developer Info

**Developed by Akshit Singh**  

- 💻 GitHub: [@akshit-singhh](https://github.com/akshit-singhh)  
- 📧 Email: akshitsingh658@gmail.com  
- 🔗 LinkedIn: [linkedin.com/in/akshit-singhh](https://www.linkedin.com/in/akshit-singhh)

---

# ⭐ Support

If you like this project, please **star this repository 🌟** and consider contributing ideas or improvements!
