# AdoetzGPT Flash

> Android app for the modified Open WebUI client — built with a native WebView wrapper and a Vite-bundled setup layer.

---

## Architecture Overview

```
AdoetzGPT Flash/
├── frontend/               ← Bundled setup/loader page (Vite)
│   ├── index.html          ← Setup screen, loading, error screens
│   ├── src/
│   │   ├── main.js         ← Backend URL logic, ping, localStorage
│   │   └── setup.css       ← Dark glassmorphism UI
│   └── vite.config.js
│
├── android/                ← Native Android project
│   ├── app/src/main/
│   │   ├── java/com/adoetz/gpt/flash/
│   │   │   ├── MainActivity.kt          ← WebView host
│   │   │   ├── FlashWebViewClient.kt    ← URL routing
│   │   │   ├── FlashWebChromeClient.kt  ← Mic/file permissions
│   │   │   ├── NativeBridge.kt          ← JS↔Native bridge
│   │   │   ├── FlashApplication.kt
│   │   │   ├── service/
│   │   │   │   └── VoiceSessionService.kt  ← Foreground mic service
│   │   │   └── utils/
│   │   │       └── BackendPreferences.kt   ← DataStore persistence
│   │   ├── assets/public/              ← Frontend build output (copied by CI)
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
│
├── capacitor.config.json
├── package.json
├── .github/workflows/build-flash.yml
└── README.md
```

---

## How It Works

1. **First launch**: The WebView loads the bundled `index.html` (from `android/app/src/main/assets/public/`).
2. **Setup screen**: User enters the Open WebUI backend URL. It is validated with a ping and saved to localStorage + Android DataStore.
3. **Redirect**: The bundled page performs `window.location.href = backendUrl`, navigating the WebView to the remote Open WebUI server.
4. **Subsequent launches**: The backend URL is read from localStorage — the bundled page immediately redirects without showing the setup UI.
5. **Voice sessions**: When the user activates voice/live conversation mode in Open WebUI, the frontend calls `FlashNative.startVoiceSession()`, which starts an Android foreground service. This keeps the microphone alive when the app is backgrounded or the screen is locked.

---

## Build Instructions

### Prerequisites

- Node.js ≥ 18
- Java 17 (JDK)
- Android SDK (API 35 / Android 15)

### Local Build

```bash
# 1. Enter the project directory
cd "AdoetzGPT Flash"

# 2. Install Node dependencies
npm install

# 3. Install frontend dependencies
cd frontend && npm install && cd ..

# 4. Build the frontend
cd frontend && npm run build && cd ..

# 5. Copy frontend build to Android assets
mkdir -p android/app/src/main/assets/public
cp -r frontend/build/. android/app/src/main/assets/public/

# 6. Build the Android APK
cd android
chmod +x gradlew
./gradlew assembleDebug

# 7. Find the APK
find app/build/outputs/apk/debug -name "*.apk"
```

### GitHub Actions (Recommended)

Push to `main` or `develop`, or trigger manually via **Actions → Build AdoetzGPT Flash APK → Run workflow**.

The workflow:
1. Installs Node.js dependencies
2. Builds the Vite frontend
3. Copies the build to Android assets
4. Builds the Debug APK
5. Uploads it as a downloadable workflow artifact

---

## GitHub Actions Secrets

| Secret | Required | Description |
|--------|----------|-------------|
| `KEYSTORE_BASE64` | Release only | Base64-encoded `.jks` keystore file |
| `KEYSTORE_PASSWORD` | Release only | Keystore password |
| `KEY_ALIAS` | Release only | Key alias name |
| `KEY_PASSWORD` | Release only | Key password |

For debug builds, **no secrets are required**.

To generate and encode a keystore:
```bash
keytool -genkey -v -keystore adoetzgpt-flash.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias adoetzgpt -storepass YOUR_STORE_PASS -keypass YOUR_KEY_PASS

# Encode for GitHub secret
base64 -i adoetzgpt-flash.jks | pbcopy   # macOS - pastes to clipboard
```

---

## Changing Backend URL

- From the app: The frontend (Open WebUI) does not have a native settings entry point yet. You can add one by calling `window.location.href = 'file:///android_asset/public/index.html'` from any button in the Open WebUI UI, or by calling `FlashNative.openSettings()` from JS.
- After logout from Open WebUI: The app returns to the bundled setup page automatically if the frontend calls `FlashNative.clearBackendUrl()` and reloads the bundled URL.

---

## Voice / Microphone Background Behavior

- When the user enters a live voice conversation, JavaScript calls `FlashNative.startVoiceSession()`.
- Android starts `VoiceSessionService` as a foreground service with type `microphone`.
- A persistent notification appears: **"AdoetzGPT Flash — Voice Active"**.
- This satisfies Android 12+ background microphone restrictions.
- WebSocket and audio streaming continuity is maintained by the Open WebUI frontend.
- When the voice session ends, JavaScript calls `FlashNative.stopVoiceSession()` to dismiss the notification and release the wakelock.

---

## Safe Area / Viewport

- The app uses `WindowCompat.setDecorFitsSystemWindows(window, false)` for edge-to-edge display.
- `android:windowLayoutInDisplayCutoutMode="shortEdges"` handles camera cutouts.
- The frontend CSS uses `env(safe-area-inset-*)` for proper padding.
- The Open WebUI frontend is expected to handle its own safe-area CSS.

---

## File Upload / Download

- `FlashWebChromeClient.onShowFileChooser()` intercepts file picker requests from Open WebUI.
- Camera, gallery, and document picker intents are all supported via the system file chooser.
- Downloads are handled by Android's default download manager via the WebView.

---

## External Links

- `FlashWebViewClient` intercepts all navigation.
- Links to the backend host stay inside the WebView.
- External links (different domain) are opened in the system browser via `ACTION_VIEW` intent.

---

## Known Limitations / Notes

- **Capacitor**: The `capacitor.config.json` is present for future migration to full Capacitor plugin support. The current implementation uses a native WebView directly for maximum control and compatibility.
- **mipmap icons**: PNG launcher icons are not included. The adaptive icon uses the vector mic drawable. For production, generate proper PNGs with Android Studio or an online tool.
- **Self-signed SSL**: `FlashWebViewClient.onReceivedSslError` currently proceeds on SSL errors for development. For production, call `handler?.cancel()` instead.
- **gradlew binary**: The `gradlew` shell script must be committed to the repo. The GitHub Actions workflow runs `chmod +x gradlew` automatically.

---

## Not Included (By Design)

- The Open WebUI backend (Python/FastAPI) — runs remotely, not on device.
- The Open WebUI frontend source — loaded remotely from the configured backend URL.
- Any files from `AdoetzGPT edit` or `AdoetzGPT GLM`.
