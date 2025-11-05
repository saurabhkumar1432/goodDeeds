# Brownie Points App - Setup Instructions

## Prerequisites
- Android Studio (latest version)
- Firebase account
- Git

## Firebase Setup

1. **Create a Firebase Project:**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Click "Add project"
   - Follow the setup wizard

2. **Enable Authentication:**
   - In Firebase Console, go to Authentication > Sign-in method
   - Enable Google Sign-In
   - Configure OAuth consent screen

3. **Setup Firestore Database:**
   - Go to Firestore Database
   - Create database in production mode
   - Deploy the security rules:
     ```bash
     firebase deploy --only firestore:rules,firestore:indexes
     ```

4. **Add Android App to Firebase:**
   - In Project settings, click "Add app" > Android
   - Package name: `com.browniepoints.app`
   - Download `google-services.json`
   - Place it in `app/` directory

5. **Configure Firebase CLI:**
   - Install Firebase CLI: `npm install -g firebase-tools`
   - Login: `firebase login`
   - Initialize: `firebase init`
   - Copy `.firebaserc.example` to `.firebaserc` and update project ID

## Local Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd goodDeeds
   ```

2. **Add Firebase Configuration:**
   ```bash
   cp app/google-services.json.example app/google-services.json
   cp .firebaserc.example .firebaserc
   ```
   Then edit both files with your Firebase project details.

3. **Update Web Client ID:**
   - Open `app/src/main/res/values/strings.xml`
   - Replace `default_web_client_id` with your Web Client ID from Firebase Console

4. **Sync and Build:**
   - Open project in Android Studio
   - Sync Gradle files
   - Build the project

## Running the App

1. **Connect Device or Start Emulator**

2. **Run the app:**
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Security Notes

⚠️ **NEVER commit these files:**
- `google-services.json` - Contains Firebase API keys
- `.firebaserc` - Contains project IDs
- `local.properties` - Contains SDK paths
- `*.jks` / `*.keystore` - Signing keys

## Testing

Run integration tests:
```bash
firebase emulators:start
./gradlew connectedDebugAndroidTest
```

## Troubleshooting

- **Google Sign-In fails:** Ensure SHA-1 certificate fingerprint is added in Firebase Console
- **Firestore permission denied:** Deploy security rules with `firebase deploy --only firestore:rules`
- **Build errors:** Clean and rebuild with `./gradlew clean build`
