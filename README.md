# 📡 Auracast Assistant (BroadcastAssistant)

## 📌 Project Description

**Auracast Assistant** is an Android application developed using **Kotlin** and **Jetpack Compose**. It is designed to discover nearby Bluetooth Low Energy (BLE) Auracast broadcasting devices and display information about compatible connected receivers.

The application allows users to scan for Auracast broadcasters, view device information, explore available Broadcast Isochronous Stream (BIS) channels, and select channels through an interactive interface.

The project also includes Bluetooth Audio Scan Service (BASS) GATT components intended to support BIS control operations with compatible receiver devices.

## ✨ Features

- 📡 **Auracast Device Discovery:** Scan for nearby Auracast broadcasters using Bluetooth Low Energy.
- 🔍 **Start/Stop Scanning:** Manually control device scanning.
- 📱 **Device Information:** Display the device name, Bluetooth address, RSSI, and broadcast ID.
- 🎧 **Receiver Discovery:** Display compatible paired or connected Bluetooth receivers.
- 🎵 **BIS Channel Information:** Display available BIS channels, their indexes, and language information when provided by advertisement metadata.
- ✅ **BIS Channel Selection:** Select one or multiple BIS channels through the user interface.
- 🎨 **Interactive UI:** Built using Jetpack Compose and Material 3.
- 🔐 **Bluetooth Permissions:** Request the required device permissions at runtime.
- 💬 **Scan Status:** Display scanning progress, permission warnings, and device discovery results.
- ⚙️ **BASS GATT Components:** Includes source code for reading receiver state and constructing control-point commands.

**Current status:** The BIS selection screen is implemented, but sending selection commands through the screen is not yet fully connected to the ViewModel.

## 🛠️ Technologies Used

| Technology | Description |
|---|---|
| Kotlin | Android application development |
| Android Studio | Development environment |
| Jetpack Compose | User interface |
| Material 3 | UI components |
| Bluetooth Low Energy | Wireless device discovery |
| Bluetooth GATT | BASS receiver communication |
| MVVM Architecture | Application state management |
| Kotlin Coroutines | Asynchronous operations |
| StateFlow | Reactive UI state |
| Navigation Compose | Screen navigation |
| DataStore | Local preference storage |
| Gradle | Build management |

## 📂 Project Structure

```text
BroadcastAssistant/
│
├── app/
│   ├── src/main/
│   │   ├── java/com/android/broadcastassistant/
│   │   │   ├── MainActivity.kt
│   │   │   ├── audio/
│   │   │   ├── ble/
│   │   │   ├── data/
│   │   │   ├── delegator/
│   │   │   ├── ui/
│   │   │   ├── util/
│   │   │   └── viewmodel/
│   │   │
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   │
│   ├── releases/
│   └── build.gradle.kts
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### Main Components

**MainActivity.kt**

Initializes the application, requests Bluetooth permissions, and launches the Jetpack Compose interface.

**AuracastEaScanner.kt**

Scans for nearby Auracast BLE advertisements and maintains the discovered broadcaster list.

**BisParser.kt and BisParserHelper.kt**

Process broadcast-related data and extract available BIS information.

**ConnectedReceiverManager.kt**

Identifies compatible paired or connected Bluetooth receiver devices.

**BisChannelScreen.kt**

Displays available BIS channels and allows users to select them.

**BassGattManager.kt**

Contains Bluetooth GATT functionality for BASS control and receiver-state operations.

**AuracastViewModel.kt**

Manages scanning, device discovery, permissions, and BIS command-related application state.

## 🚀 Installation and Setup

### Prerequisites

- Android Studio
- Android SDK
- Kotlin and Gradle
- A physical Android device with Bluetooth LE support
- Bluetooth enabled
- Compatible Auracast broadcasting hardware for testing

Android 13 or newer is recommended for testing the current implementation.

### Step 1: Clone the Repository

```bash
git clone https://github.com/ashivarani/BroadcastAssistant.git
```

### Step 2: Open in Android Studio

1. Launch Android Studio.
2. Select **Open**.
3. Navigate to the cloned BroadcastAssistant project.
4. Allow Gradle synchronization to complete.

### Step 3: Run the Application

1. Connect an Android device.
2. Enable Bluetooth.
3. Select the connected device in Android Studio.
4. Click **Run**.
5. Grant the required permissions.

## 📱 How to Use

1. Open the **Auracast Assistant** application.
2. Allow the requested Bluetooth permissions.
3. Tap **Start Scan** to discover nearby Auracast devices.
4. View the discovered broadcasters and compatible receivers.
5. Tap an available broadcaster to open its BIS channel screen.
6. View the available BIS channels and select the required indexes.
7. Use **Apply Selection** to interact with the selection interface.
8. Return to the main screen and tap **Stop Scan** when finished.

**Note:** In the supplied version, Apply Selection does not yet transmit the BIS command because the required ViewModel is not passed to the BIS screen.

## ⚙️ How It Works

### 1. Bluetooth Device Scanning

The application uses BLE scanning to detect Auracast broadcasters advertising the Broadcast Audio service.

Discovered devices are processed and displayed in the application.

### 2. Device Information

The application displays available information about discovered devices, including:

- Device name
- Bluetooth address
- RSSI signal strength
- Broadcast identifier

### 3. BIS Channel Discovery

The application processes available broadcast metadata to identify BIS channels.

Each available channel may display:

- BIS index
- Language
- Audio role
- Stream configuration

Additional details are shown when present in the parsed information.

### 4. BIS Channel Selection

Users can interact with BIS channel cards to select or deselect available channels.

The project contains underlying functionality for building and sending BASS commands, although the current UI integration remains incomplete.

### 5. Application State Management

The application uses ViewModel, StateFlow, and Kotlin Coroutines to manage scan results, device lists, permissions, and status updates.

## ⚠️ Current Limitations

- BIS selection commands are not fully connected to the current navigation flow.
- BASS communication requires compatible receiver hardware and a working Bluetooth GATT connection.
- Encrypted broadcast selection is not supported in the current implementation.
- Some broadcasters may not expose enough metadata to display BIS channels.
- The application does not provide its own audio playback or audio streaming interface.
- Although the build configuration specifies API 26 as the minimum SDK, compatibility below Android 13 requires further verification.

## 🎯 Learning Outcomes

This project demonstrates:

- Android application development using Kotlin.
- Building interfaces with Jetpack Compose.
- Implementing Bluetooth Low Energy scanning.
- Processing Auracast advertisement data.
- Understanding BIS channels and broadcast audio metadata.
- Exploring BASS GATT communication.
- Using MVVM architecture with ViewModel and StateFlow.
- Managing permissions and asynchronous operations.
- Implementing navigation between Compose screens.

## 📌 Project Information

| Detail | Value |
|---|---|
| Project Name | BroadcastAssistant |
| App Name | Auracast Assistant |
| Platform | Android |
| Programming Language | Kotlin |
| UI Framework | Jetpack Compose |
| Architecture | MVVM |
| Bluetooth Technology | Bluetooth Low Energy |
| Application Version | 3.0.1 |
| Compile SDK | 36 |
| Target SDK | 34 |
| Configured Minimum SDK | 26 |
| Development Environment | Android Studio |

## 📦 Debug APK Builds

The project contains previously generated debug APKs in the `app/releases/` directory, including a version 3.0.1 debug build.

To create a new debug APK from the complete project, use:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The generated debug APK is normally located at:

`app/build/outputs/apk/debug/app-debug.apk`

## 🔗 GitHub Repository

https://github.com/ashivarani/BroadcastAssistant

## 📝 Conclusion

**Auracast Assistant** demonstrates the use of Bluetooth Low Energy, Auracast device discovery, and BIS channel information in an Android application developed using Kotlin and Jetpack Compose.

The project provides a foundation for understanding Bluetooth broadcast audio technologies and developing applications that interact with compatible Auracast broadcasters and receivers.
