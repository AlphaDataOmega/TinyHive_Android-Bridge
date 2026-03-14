# TinyHive Bridge - Android App

Android companion app that enables TinyHive controllers to control apps on your device.

## Features

- **App Control**: Open any app, tap buttons, read screen content
- **Gestures**: Swipe, scroll, long press
- **Text Input**: Type into any input field
- **Navigation**: Back, home, recents, notifications
- **Always Connected**: Runs as a foreground service, reconnects automatically

## Setup

1. Install the app (APK or Play Store)
2. Open TinyHive Bridge
3. Grant Accessibility permission (Settings → Accessibility → TinyHive Bridge → Enable)
4. Enter your hive URL (e.g., `https://your-hive.example.com`)
5. Tap Connect

## Supported Commands

| Action | Description | Parameters |
|--------|-------------|------------|
| `identify` | Get device info | - |
| `ping` | Health check | - |
| `open_app` | Launch an app | `package`: package name |
| `tap` | Tap element by text/id | `text`, `resource_id`, `content_description` |
| `tap_coordinates` | Tap at x,y | `x`, `y` |
| `long_press` | Long press at x,y | `x`, `y` |
| `swipe` | Swipe gesture | `direction`: up/down/left/right |
| `scroll` | Scroll in view | `direction`: up/down |
| `type_text` | Type into focused field | `text` |
| `read_screen` | Get all screen elements | - |
| `back` | Press back button | - |
| `home` | Press home button | - |
| `recents` | Open recent apps | - |
| `notifications` | Open notification shade | - |
| `get_current_app` | Get foreground app | - |

## Building

```bash
cd android
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Android Phone                   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │          TinyHive Bridge App            │   │
│  │                                         │   │
│  │  ┌─────────────┐  ┌──────────────────┐ │   │
│  │  │ MainActivity│  │ BridgeService    │ │   │
│  │  │ (Setup UI)  │  │ (WebSocket)      │ │   │
│  │  └─────────────┘  └────────┬─────────┘ │   │
│  │                            │            │   │
│  │  ┌─────────────────────────▼──────────┐│   │
│  │  │  TinyHiveAccessibilityService     ││   │
│  │  │  - Read screen content            ││   │
│  │  │  - Perform taps/gestures          ││   │
│  │  │  - Execute commands               ││   │
│  │  └────────────────────────────────────┘│   │
│  └─────────────────────────────────────────┘   │
│                      │                          │
│              Accessibility API                  │
│                      │                          │
│  ┌──────────────────▼───────────────────────┐  │
│  │           Any App (Spotify, etc.)        │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
└─────────────────────────────────────────────────┘
           │
           │ WebSocket (wss://)
           │
     ┌─────▼─────┐
     │ TinyHive  │
     │  Server   │
     └───────────┘
```

## Permissions Required

- **Accessibility Service**: Read/control other apps
- **Internet**: Connect to TinyHive server
- **Foreground Service**: Keep connection alive
- **Boot Completed**: Auto-start on device boot

## Privacy

- All commands come from YOUR hive (you control it)
- No data sent to third parties
- Connection is encrypted (WSS/HTTPS)
- You can disconnect anytime
