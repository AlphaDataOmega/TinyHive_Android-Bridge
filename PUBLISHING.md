# Publishing TinyHive Bridge to Google Play

## Prerequisites

1. **Google Play Developer Account** ($25 one-time fee)
   - Sign up at: https://play.google.com/console

2. **Signing Key** (already generated)
   - Keystore: `tinyhive-release.jks`
   - Keep this file safe - you cannot update the app without it!

## GitHub Secrets Setup

Add these secrets to the repository (Settings → Secrets → Actions):

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64-encoded keystore file |
| `KEYSTORE_PASSWORD` | `tinyhive123` (change this!) |
| `KEY_ALIAS` | `tinyhive` |
| `KEY_PASSWORD` | `tinyhive123` (change this!) |

To encode the keystore:
```bash
base64 -w 0 tinyhive-release.jks
```

## Creating a Release

1. **Update version** in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2  // Increment this
   versionName = "1.1.0"  // Update as needed
   ```

2. **Create a git tag**:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

3. **GitHub Actions will automatically**:
   - Build the APK and AAB
   - Sign both with your keystore
   - Create a GitHub Release with the files

## Play Store Submission

### First-Time Setup

1. Go to [Google Play Console](https://play.google.com/console)
2. Create a new app
3. Fill in store listing:
   - **App name**: TinyHive Bridge
   - **Short description**: Use from `fastlane/metadata/android/en-US/short_description.txt`
   - **Full description**: Use from `fastlane/metadata/android/en-US/full_description.txt`
4. Upload screenshots (1080x1920 recommended)
5. Set content rating (complete questionnaire)
6. Set up pricing (Free)
7. Add privacy policy URL

### Upload AAB

1. Go to Release → Production
2. Create new release
3. Upload the signed `.aab` file from GitHub Releases
4. Add release notes from `fastlane/metadata/android/en-US/changelogs/`
5. Submit for review

### Privacy Policy Hosting

Host `PRIVACY_POLICY.md` somewhere publicly accessible:
- GitHub Pages: `https://alphadataomega.github.io/TinyHive_Android-Bridge/privacy`
- Your website: `https://yourdomain.com/privacy/tinyhive-bridge`

## Required Assets

### Screenshots (at least 2)
- Phone: 1080x1920 or 1440x2560
- Show: Connection screen, paired state, QR scanning

### Feature Graphic
- Size: 1024x500
- Show: App logo + tagline

### App Icon
- Size: 512x512
- High-res version of app icon

## Content Rating

When filling out the content rating questionnaire:
- Violence: None
- Sexuality: None
- Language: None
- Controlled substances: None
- User interaction: Yes (connects to user's server)
- Data sharing: No (only to user's own server)

## Accessibility Declaration

Since the app uses Accessibility Service, Google may require:
1. Video showing legitimate use
2. Explanation of why accessibility is needed
3. Privacy policy covering accessibility data use

**Explanation template**:
> TinyHive Bridge uses Accessibility Service to enable users to control their phone apps hands-free through their personal TinyHive AI assistant. The service reads screen content and performs taps/gestures only when commanded by the user's own server. No data is sent to third parties.

## Timeline

- First submission: 1-7 days review
- Updates: Usually 1-3 days
- Rejection: Fix issues and resubmit

## Troubleshooting

**"App not signed correctly"**
- Ensure you're uploading the signed AAB, not unsigned
- Check that GitHub secrets are set correctly

**"Accessibility Service rejected"**
- Provide clear video demonstration
- Emphasize user controls the server
- Link to open source code

**"Privacy policy missing/invalid"**
- Ensure URL is publicly accessible
- Must mention Accessibility Service usage
