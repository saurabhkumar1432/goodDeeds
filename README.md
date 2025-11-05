# Brownie Points App

A simple Android application for two people to reward each other with brownie points and optional messages.

## Firebase Setup Instructions

To complete the Firebase configuration, you need to:

1. **Create a Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project or use an existing one
   - Enable Google Analytics (optional)

2. **Add Android App to Firebase Project**
   - Click "Add app" and select Android
   - Enter package name: `com.browniepoints.app`
   - Enter app nickname: `Brownie Points App`
   - Add SHA-1 certificate fingerprint (for Google Sign-In)

3. **Download google-services.json**
   - Download the `google-services.json` file from Firebase Console
   - Replace the placeholder file in `app/google-services.json`

4. **Enable Firebase Services**
   - **Authentication**: Enable Google Sign-In provider
   - **Firestore**: Create database in production mode
   - **Cloud Messaging**: Automatically enabled

5. **Configure Google Sign-In**
   - In Firebase Console, go to Authentication > Sign-in method
   - Enable Google provider
   - Add your SHA-1 certificate fingerprint
   - Note the Web client ID for later use

## Project Structure

```
app/
├── src/main/java/com/browniepoints/app/
│   ├── data/
│   │   ├── model/          # Data models (User, Connection, Transaction)
│   │   ├── repository/     # Repository interfaces
│   │   └── service/        # Firebase messaging service
│   ├── domain/
│   │   └── usecase/        # Business logic use cases
│   ├── presentation/
│   │   └── ui/theme/       # UI theme and styling
│   └── di/                 # Dependency injection modules
```

## Technologies Used

- **Kotlin** - Programming language
- **Jetpack Compose** - Modern UI toolkit
- **Firebase Authentication** - User authentication with Google Sign-In
- **Cloud Firestore** - Real-time database with offline support
- **Firebase Cloud Messaging** - Push notifications
- **Hilt** - Dependency injection
- **MVVM Architecture** - Clean architecture pattern
- **Coroutines** - Asynchronous programming

## Getting Started

1. Clone the repository
2. Complete Firebase setup (see instructions above)
3. Open project in Android Studio
4. Sync project with Gradle files
5. Run the app on an emulator or device

## Features

- Google Sign-In authentication
- User connection via matching codes
- Give brownie points (1-10 range)
- Optional messages with points
- Real-time transaction history
- Push notifications
- Offline support with automatic sync

## Next Steps

This is the basic project structure. The next tasks will implement:
1. Data models and Firebase integration
2. Authentication system
3. User management and connections
4. Points transaction system
5. UI screens and navigation
6. Notification system
7. Offline functionality