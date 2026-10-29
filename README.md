# BlueWave Radio
Ad-Free - No Paywall - Car-Ready Radio

**BlueWave** is an open-source Android application for worldwide radio streaming, forked from [AMARadio](https://github.com/ounben/AMARadio) (GPLv3). By leveraging the community-supported [radio-browser.info](https://www.radio-browser.info/) database, BlueWave provides instant access to thousands of stations in a stable, modern, and user-friendly environment.

## About this fork

**BlueWave** is an ad-free, tracking-free, paywall-free car radio app for Android. It is a personal fork of [AMARadio](https://github.com/ounben/AMARadio) with a blue redesign, location-based Home groups (city / province / national / English stations), automatic car mode over Bluetooth, and steering-wheel station switching.

## Acknowledgments

Special thanks to the original [AMARadio](https://github.com/ounben/AMARadio) project by ounben, and to the [RadioDroid](https://github.com/Segler-Alex/RadioDroid) project for the inspiration and ground-breaking work in open-source radio streaming.

## Key Features

- **Instantaneous UI**: Thanks to a highly optimized architecture, switching between tabs and opening the player occurs without delay or flickering.
- **Google Cast (Chromecast) Support**: Stream directly to speakers and TVs using the modern **Media3 Cast** framework. Features a native Compose-based connection button for easy device management.

- **Advanced Android Auto Integration**:
  - **Stability (Media-Anchor)**: Specialized logic to prevent session disconnects during station switches on modern Android versions (15+).
  - **HLS/TS Support**: Full compatibility with complex Transport Stream formats.
  - **Optimized UI**: Compact list designs, driver-safe folder hierarchy, and prioritized metadata (Station Name first) for maximum legibility.

- **Modern Home Screen Widgets**: Feature-rich widgets based on **Jetpack Glance**.
  - **Anti-Ghost Architecture**: Optimized to prevent "black app" entries in Android Recents via direct background service starts.
  - **Sequential Sync**: High-performance reactive model with Mutex-based update sequencing for 100% state consistency.
  - **Live Track Info**: Real-time Artist and Song Title displays using optimized Media3 event listeners.
  - **Professional Previews**: Adaptive XML-based previews in the system widget picker.

- **Custom Radio Stations**: A dedicated management system for personal streams.
  - **Native Drag & Drop**: Smooth, high-performance reordering using the latest Compose 1.7 APIs.
  - **Local Image Support**: Use your own icons for personal streams, with automated permanent storage.
  - **Smart Catalog Matching**: Automatically retrieves metadata and tags if a custom URL matches an entry in the community database.

- **Comprehensive Accessibility**: Fully optimized for **TalkBack** and screen readers. Features semantic grouping of information, localized accessibility strings in over 74 languages, and "speaking" status icons for fluid navigation.

- **Advanced Search & Filtering**: Find stations by Name, Country, Language, or Tags. Metadata for over **11,000 tags** is cached locally in a high-performance SQL database to enable instant offline suggestions.

- **Community Support (Click Counting)**: Supports the global ranking of radio stations by reporting playback clicks directly to the official radio-browser.info API.
- **Dynamic UI Scaling**: Custom settings allow for the adjustment of the user interface size from Compact to Extra Large.

- **Broad Language Support**: Support for over **74 languages**, including a variety of African languages (Afrikaans, Amharic, Swahili, Zulu) with a dedicated in-app language selector.

- **Ogg & Opus Metadata**: Full dynamic track information support for Ogg Vorbis and Opus streams, including seamless updates for chained streams.

- **Modern Design**: Developed with **Jetpack Compose**, featuring seamless support for Dark/Light modes and edge-to-edge system integration.

- **High-Performance Streaming**: A robust engine based on **AndroidX Media3 (ExoPlayer)** with real-time audio thread prioritization, strict audio focus handling, dynamic session management, and asynchronous network processing to prevent UI lag.

- **Optimized Image Loading**: Leverages the **Coil** framework across the entire app, including the Media3 Session, to ensure memory-efficient and lightning-fast delivery of station icons and artwork.

- **Data Consistency**: Specialized logic for instant synchronization between the Smartphone UI, Home Screen Widgets, and Android Auto units using direct SQL access and proactive reactive flows.

- **High Availability**: Integrated failover support via mirror servers ensures uninterrupted station browsing even if the primary database is offline.

- **Smart Management**: Includes a sleep timer, efficient favorites management, and full support for M3U playlist export/import.

- **Robust Data Resilience**: Features a unique **Dual-Database Architecture** that strictly separates the global radio catalog from personal user data (Favorites, History, Filters). This ensures your personal settings are never lost during global database updates.

## Getting Started

### Installation
Download the latest APK from the [GitHub releases page](https://github.com/benjamin-em/BlueWave/releases), or build it from source as described below.

### Building from Source
To build the project locally, ensure you have the latest version of Android Studio installed.

1. Clone the repository:
   ```bash
   git clone https://github.com/benjamin-em/BlueWave.git
   ```
2. Open the project in **Android Studio**.
3. Build using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```

## Technical Specification

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose & Material 3
- **Architecture**: MVVM with Activity-scoped ViewModels for state management
- **Persistence**:
  - **Station Catalog**: High-performance SQL for the worldwide radio database.
  - **User Storage**: Dedicated SQL-based **UserDatabase** for Favorites, History, and custom Filters (migrated from legacy JSON).
- **Networking**: OkHttp 5 & Coroutines for API communication, Coil for async image loading
- **Media Engine**: AndroidX Media3 (ExoPlayer & CastPlayer)
- **Target SDK**: 37 (Android 17)
- **Minimum SDK**: 26 (Android 8.0)

## UML

<p align="left">
  <img src="docs/UML/UML_AMARadio_Custom_Favorit.png" width="100%" />
</p>

## Contributing

Contributions are welcome and appreciated. If you wish to improve the codebase, fix bugs, or update translations, please follow these steps:
- Report issues via the [GitHub Issue Tracker](https://github.com/benjamin-em/BlueWave/issues).
- Submit improvements via Pull Requests.
- Translation updates are highly encouraged to improve global accessibility.

## License

This project is licensed under the **GNU General Public License v3.0**. Detailed information can be found in the [LICENCE](LICENCE) file.

---
<p align="left">
  AMARadio - Ad-Free - No Paywall - Just Radio.
</p>
