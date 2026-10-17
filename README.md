# 🔔 AlarmSimple

**Free, open-source smart alarm clock** with real sleep tracking, smart wake-up in light sleep phase, and configurable dismissal difficulty — from a simple button to NFC tags, barcode scanning, and math quests.

No ads. No tracking. No subscription.

## ✨ Features

### Two Alarm Modes
- **Classic** — Set a specific wake-up time (e.g., 7:00 AM)
- **Sleep Duration** — Set how long you want to sleep (e.g., 7.5 hours). The app detects when you actually fall asleep via microphone + accelerometer and starts the timer from that moment.

### 🧠 Smart Wake-Up (optional)
- Define a wake window around your target time (e.g., 30 minutes before)
- The app monitors your sleep phases and wakes you during light sleep
- If no light phase is detected, the alarm fires at the window boundary
- Gradual sound increase for a gentle wake-up

### 🎯 Dismissal Quests (optional)
Choose your difficulty:
- **NFC Tag** — Walk to another room and tap your phone to a tag
- **Barcode** — Scan a specific product (e.g., toothpaste in the bathroom)
- **Math Blitz** — Solve 3 problems (15 seconds each; mistakes reset progress)

### 🚨 Siren Mode
- Activates if you don't complete the quest within 5 minutes
- Maximum volume, no dismiss button — only the quest can stop it
- Optional hardware volume lock (prevents lowering volume)

### 📊 Morning Statistics
- Hypnogram showing sleep phases throughout the night
- When you actually fell asleep
- Total sleep duration and efficiency
- Movement and snoring data

## 🏗️ Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Barcode | ML Kit |
| Camera | CameraX |
| Charts | Vico |
| Min SDK | 26 (Android 8.0) |

## 🧪 Sleep Tracking — How It Works

The app uses two sensors to track your sleep:

**Accelerometer (primary)** — Phone on the mattress picks up body movements. Movement Intensity (MI) is computed per 30-second epoch and classified into sleep phases.

**Microphone (secondary)** — Burst recording (10s on / 20s off) analyzes ambient sound. Detects silence (sleep onset), snoring (deep sleep), and speech (wakefulness). No audio is ever stored.

| Metric | Expected Accuracy |
|---|---|
| Sleep/Wake detection | ~90–95% |
| Light vs Deep sleep | ~70–80% |
| REM detection | ~55–65% |
| Smart wake timing | ~85–90% |

## 📱 Supported Languages

- 🇬🇧 English (default)
- 🇷🇺 Russian

## 🔒 Privacy

- **No internet permission** — the app works completely offline
- **No audio stored** — microphone data is processed in real-time and discarded
- **No analytics or tracking** — your sleep data stays on your device
- **Open source** — verify it yourself

## 📄 License

MIT License — see [LICENSE](LICENSE)

## 🤝 Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.
