# 🔔 AlarmSimple

<p align="center">
  <b>Free and Open Source Smart Alarm Clock</b><br>
  Real sleep tracking, smart wake-up during light sleep phases, nocturnal sound recorder, and wake-up quests.
</p>

<p align="center">
  <a href="README.md">Русский</a> | <b>English</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.1-purple.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/No_Ads-100%25-brightgreen.svg" alt="No Ads">
</p>

---

**AlarmSimple** is an ad-free, subscription-free, and tracker-free smart alarm clock. The app helps you wake up feeling refreshed by analyzing your sleep cycles and guarantees you get out of bed using dismiss quests and anti-tamper volume locks.

---

## ✨ Features

### 1. Two Alarm Modes (Configurable)
* **Sleep Duration Mode:** Set how much sleep you need (e.g., 7.5 hours). The app detects silence and regular breathing — the sleep timer starts **only after you actually fall asleep**.
* **Classic Mode:** A traditional alarm clock for a specific time (e.g., 7:00 AM).

### 2. Smart Wake-Up Window
* Set a configurable time window (e.g., 15–45 minutes before target wake time).
* Within this window, the app detects the transition from deep sleep to light sleep (via movement and rustling) and begins playing a gentle, gradually increasing alarm sound.
* If no light phase is detected, the alarm triggers exactly at the window deadline.

### 3. Dismissal Quests
When quests are enabled, the normal "Dismiss" button is hidden — only a **"Give 5 Minutes"** button is available, which temporarily mutes the alarm:
* **NFC Tag:** Walk to another room and tap your phone to an NFC sticker.
* **Barcode:** Scan a chosen item with the camera (e.g., toothpaste in the bathroom).
* **Math Blitz:** Solve 3 mental math problems (15 seconds per problem; any mistake resets progress).

### 4. Siren Mode & Anti-Tamper
* If the quest is not completed within 5 minutes, an ear-splitting siren triggers at maximum volume.
* All dismiss buttons disappear: sound can **only** be stopped by completing the quest.
* **Hardware Volume Lock:** Volume rocker keys are intercepted and locked at 100%.

### 5. Morning Statistics & Sleep Audio
* **Clear Hypnogram:** Night sleep stage visualization (Deep, Light, REM, Awake).
* **Night Sound Snippets:** Smart recording captures interesting audio events (snoring, sleep talking, coughing, strange noises) for morning playback.
* **Smart Media Filtering:** If you fall asleep watching videos or podcasts, the system recognizes continuous external audio and avoids false triggers.

---

## 🔒 Privacy & Battery Efficiency

* **100% Offline:** No `INTERNET` permission. Your sleep data and audio snippets never leave your device.
* **Storage Friendly:** No continuous 8-hour audio files. Only short 10–12 second clips are saved in compressed AAC format (~30 KB per clip).
* **Battery Conscious:** Hardware sensor batching and CPU partial sleep limits overnight battery drain to **~8–15% over 8 hours**.

---

## 🛠️ Tech Stack

* **Language:** Kotlin 2.1
* **UI:** Jetpack Compose + Material 3 (Dynamic Color & System Theme support)
* **Architecture:** Clean Architecture + MVVM
* **DI:** Dagger Hilt
* **Database:** Room Database
* **Preferences:** Jetpack DataStore Preferences
* **Barcode Scanner:** Google ML Kit Barcode Scanning + CameraX
* **Build System:** Gradle Version Catalogs (Kotlin DSL)
* **Min SDK:** Android 8.0 (API 26)
* **Target SDK:** Android 15 (API 35)

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
