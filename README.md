# NEXORA — Firebase-connected Android starter

This is an early Android Studio starter project for NEXORA.

## Included
- Kotlin + Jetpack Compose starter screen
- Firebase Android configuration (`google-services.json`)
- Firebase Auth and Firestore dependencies
- Google Services Gradle plugin
- Package name: `com.nexora.app`

## Build
1. Install Android Studio on a computer.
2. Open this folder as an existing Gradle project.
3. Let Gradle sync complete.
4. Connect an Android phone with USB debugging enabled, or create an emulator.
5. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Important
This is not a finished app or APK. It is a Firebase-wired starter. Authentication screens,
Firestore rules/data model, Facebook login, LiveKit voice rooms, background audio service,
and production security still need to be implemented and tested.

Do not commit private server secrets. The Firebase Android config file is included as requested;
restrict API key usage in Google Cloud/Firebase settings where appropriate.
