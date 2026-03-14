# Privacy Policy for TinyHive Bridge

**Last updated: March 2025**

## Overview

TinyHive Bridge ("the App") is a companion application that connects your Android device to your personal TinyHive instance. This privacy policy explains how the App handles your data.

## Data Collection

### What We Don't Collect
- We do not collect personal information
- We do not track your location
- We do not access your contacts, photos, or files
- We do not display advertisements
- We do not share any data with third parties

### What the App Accesses
The App requires Accessibility Service permission to:
- Read on-screen text (to report what's visible)
- Perform taps and gestures (to control apps on your behalf)
- Detect which app is in the foreground

**This data is only sent to YOUR personal TinyHive server** that you configure during setup. We have no access to this data.

## Data Transmission

- All communication is between your device and your personal TinyHive server
- Connections use secure WebSocket (WSS) when your server supports HTTPS
- No data is transmitted to AlphaDataOmega or any third party

## Data Storage

The App stores locally on your device:
- Your TinyHive server URL
- Authentication tokens for reconnection
- No other data is stored

## Your Control

You can at any time:
- Disconnect from your TinyHive server
- Disable the Accessibility Service
- Uninstall the App
- Clear app data to remove all stored information

## Open Source

TinyHive Bridge is open source. You can review the complete source code at:
https://github.com/AlphaDataOmega/TinyHive_Android-Bridge

## Children's Privacy

The App is not intended for children under 13 and we do not knowingly collect data from children.

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be posted in the app's GitHub repository.

## Contact

For questions about this privacy policy:
- GitHub: https://github.com/AlphaDataOmega/TinyHive_Android-Bridge/issues

## Permissions Explained

| Permission | Why It's Needed |
|------------|-----------------|
| Internet | Connect to your TinyHive server |
| Accessibility Service | Read screen content and control apps |
| Camera | Scan QR codes for pairing |
| Foreground Service | Keep connection alive in background |
| Boot Completed | Reconnect after device restart |

---

© 2025 AlphaDataOmega. All rights reserved.
