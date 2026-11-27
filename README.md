# Select Tube

[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.4)-2979FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/justcooking/Select-Tube/releases/download/v1.0.4/SelectTube-v1.0.4.apk)
[![Release](https://img.shields.io/github/v/release/justcooking/Select-Tube?style=for-the-badge&color=success)](https://github.com/justcooking/Select-Tube/releases/latest)

> 📲 **[Click here to download SelectTube v1.0.4 APK](https://github.com/justcooking/Select-Tube/releases/download/v1.0.4/SelectTube-v1.0.4.apk)** directly to your Android phone!

A strictly curated, subscription-only YouTube client for Android. Select Tube shows videos **only** from channels and playlists you explicitly approve — everywhere in the app. There are no algorithmic recommendations, no trending pages, no comment rabbit holes, and zero ads.

Inspired by NewPipe's extractor technology, Select Tube is built entirely from scratch as a modern, distraction-free Jetpack Compose application.

---

## 🌟 Core Philosophy

- **Zero Algorithmic Traps:** The home feed only displays chronological uploads from your curated subscriptions.
- **Strict Content Isolation (Fail-Closed):** Only videos verified by canonical channel IDs (`UC...`) or exact handles (`@...`) appear in feeds, searches, or playback. Display-name collisions are rejected.
- **Intentional Friction:** New additions are governed by deliberate psychological cooling-off periods or parental authentication, preventing impulsive binge-adding.
- **Privacy & Independence:** No Google account or YouTube login required. Operates via [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor).
- **Background & Lock Screen Playback:** Seamless background audio with Media3 `MediaSessionService` support.

---

## 🛡️ Dual Operating Modes

Select Tube supports two distinct modes selected during initial onboarding:

### 1. Adult Mode (Self-Discipline & Focus)
- **Passwordless:** No master password required to add or delete content.
- **The 2-Hour Cooling-Off Rule:**
  - The **initial setup batch** of channels activates immediately so you can start using the app right away.
  - Every **subsequent addition** enters a mandatory 2-hour pending activation period.
  - While pending, any addition can be cancelled at any time with zero friction.
  - Once the countdown finishes, the channel activates automatically.
  - *Anti-Bypass Guarantee:* Deleting all channels does not reset the first-batch exemption; the cooling-off period remains strictly enforced.

### 2. Child Mode (Parental Controls)
- **Parent Master Password:** All configuration changes, Settings screens, and additions are locked behind a secure BCrypt-hashed password.
- **Shorts Blocker:** YouTube Shorts are blocked across the entire application.
- **Curated Search Control:** Search can either be locked to only approved channels or disabled entirely.
- **Active Bedtime Schedule:** Automatically pauses playback and locks the player during scheduled bedtime hours (e.g. 9:00 PM to 7:00 AM) with a parent unlock option.
- **Daily Screen Time Limit:** Enforces a configurable daily limit (e.g., 60 minutes/day) that accumulates active playback time across foreground and background play, locking playback when reached.

---

## 📺 Content Curation & Playlists

- **Channel Curation:** Add channels via in-app search, YouTube URLs (`youtube.com/@handle`, `youtube.com/channel/UC...`), or channel IDs.
- **Curated Playlists:** Add entire series, educational courses, or music sets to a dedicated Playlists tab.
- **Playlist Preview Mode:** When opening an uncurated playlist (via search or shared link), browse the full tracklist and metadata in Preview Mode. Playback is locked until approved by the parent or confirmed for addition.
- **Recent Watch History:** A compact NewPipe-style list showing watch progress, duration badges, channel info, and 1-tap resume.
- **System Share Sheet & Deep Links:** Share any YouTube link directly to Select Tube. If the channel is not yet curated, a prompt allows parents to approve it or adults to initiate the cooling-off period.

---

## 🎬 Audio & Video Player

- **Sliding MiniPlayer Sheet:** Smooth gesture-driven bottom sheet transitions between the full player and a compact mini player floating above the navigation bar.
- **Adaptive Stream Merging:** Combines the best 1080p/720p video streams with high-bitrate audio streams via ExoPlayer's `MergingMediaSource`.
- **Playback Controls:** Double-tap to seek (10s increments with visual ripple), speed control, video quality selection, audio track switcher, subtitle selector, and chapter markers.
- **Background & Screen-Off Play:** Powered by Android `MediaSessionService` (`PlaybackService`) with system notification controls and headphone unplug auto-pause.

---

## 🛠️ Build & Installation

### Prerequisites
- **JDK 17** or newer
- **Android Studio** (Jellyfish 2023.3.1 or newer recommended)
- **Android SDK:** `compileSdk 34`, `minSdk 26` (Android 8.0 Oreo and above)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/your-username/select-tube.git
cd select-tube

# Build the Debug APK
./gradlew assembleDebug

# Install on a connected Android device or emulator
./gradlew installDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🗄️ Architecture & Tech Stack

- **UI Framework:** 100% Jetpack Compose with Material 3 design and dynamic theming.
- **Architecture:** Unidirectional Data Flow (UDF) with MVVM, Kotlin Coroutines, and `StateFlow`.
- **Dependency Injection:** Hilt.
- **Database & Persistence:** Room with explicit non-destructive schema migrations; Jetpack DataStore Preferences for settings and credentials.
- **Media Playback:** AndroidX Media3 (ExoPlayer + MediaSession).
- **YouTube Extraction:** NewPipe Extractor via OkHttp.
- **Image Loading:** Coil.
- **Security:** BCrypt for parent master password hashing.

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0 (GPLv3)** — see the [LICENSE](LICENSE) file for details.
Select Tube uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), which is licensed under GPLv3.
