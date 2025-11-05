# Brownie Points App - Integration Tests

This directory contains comprehensive integration tests for the Brownie Points App, covering all major features and user workflows.

## Test Structure

### Repository Integration Tests (`repository/`)
- `AuthRepositoryIntegrationTest.kt` - Firebase Authentication operations
- `UserRepositoryIntegrationTest.kt` - User profile management
- `ConnectionRepositoryIntegrationTest.kt` - User connection system
- `TransactionRepositoryIntegrationTest.kt` - Point transactions (give/deduct)
- `TimeoutRepositoryIntegrationTest.kt` - Timeout system for couples

### End-to-End Integration Tests
- `AuthenticationEndToEndTest.kt` - Complete authentication flow (Requirements 1.1-1.5)
- `ConnectionEndToEndTest.kt` - Connection establishment and partner interaction (Requirements 2.1-2.5)
- `TransactionEndToEndTest.kt` - Transaction system with notifications (Requirements 3.1-3.4, 5.1-5.4, 7.1-7.7)
- `TimeoutEndToEndTest.kt` - Timeout system functionality (Requirements 8.1-8.10)
- `CoupleFeatureEndToEndTest.kt` - Couple-specific features integration

### Test Infrastructure
- `BaseFirebaseTest.kt` - Base class with common setup/teardown
- `FirebaseTestModule.kt` - Hilt test module for Firebase emulator
- `FirebaseTestRunner.kt` - Custom test runner with Hilt support
- `FirebaseIntegrationTestSuite.kt` - Complete test suite

## Requirements Coverage

### Authentication (Requirements 1.1-1.5)
✅ Google Sign-In integration from start to finish  
✅ User profile creation and data persistence  
✅ Authentication state management and navigation  
✅ Automatic authentication for returning users  
✅ Profile data synchronization from Firestore  

### Connection System (Requirements 2.1-2.5)
✅ Matching code generation and validation  
✅ Connection creation and bidirectional updates  
✅ Partner data loading and real-time synchronization  
✅ Error handling for invalid matching codes  
✅ Connection state management  

### Transaction System (Requirements 3.1-3.4, 5.1-5.4, 7.1-7.7)
✅ Point giving and deducting with proper balance updates  
✅ Transaction history and real-time updates  
✅ Notification delivery for all transaction types  
✅ Atomic transaction operations  
✅ Point validation and constraints  
✅ Negative balance support for conflicts  
✅ Message validation and character limits  

### Timeout System (Requirements 8.1-8.10)
✅ Timeout request validation and daily limits  
✅ Timeout countdown and automatic expiration  
✅ Transaction disabling during active timeouts  
✅ Timeout status synchronization between partners  
✅ Daily timeout reset functionality  
✅ Timeout history tracking  

## Running the Tests

### Prerequisites
1. **Firebase CLI**: `npm install -g firebase-tools`
2. **Android Device/Emulator**: Connected and running
3. **Firebase Emulators**: Configured for auth and firestore

### Option 1: Using the Test Script
```bash
# Windows
run-integration-tests.bat

# The script will:
# 1. Start Firebase emulators
# 2. Run repository tests
# 3. Run end-to-end tests
# 4. Run complete test suite
```

### Option 2: Manual Execution

1. **Start Firebase Emulators**:
```bash
firebase emulators:start --only auth,firestore
```

2. **Run Specific Test Categories**:
```bash
# Repository integration tests
./gradlew connectedAndroidTest --tests "com.browniepoints.app.repository.*"

# End-to-end tests
./gradlew connectedAndroidTest --tests "com.browniepoints.app.*EndToEndTest"

# Complete test suite
./gradlew connectedAndroidTest --tests "com.browniepoints.app.FirebaseIntegrationTestSuite"

# Individual test classes
./gradlew connectedAndroidTest --tests "com.browniepoints.app.AuthenticationEndToEndTest"
```

3. **Run All Tests**:
```bash
./gradlew connectedAndroidTest
```

## Test Configuration

### Firebase Emulator Setup
The tests use Firebase emulators for isolated testing:
- **Auth Emulator**: `localhost:9099`
- **Firestore Emulator**: `localhost:8080`

Configuration is handled in `FirebaseTestModule.kt`:
```kotlin
// Auth emulator
auth.useEmulator("10.0.2.2", 9099)

// Firestore emulator  
firestore.useEmulator("10.0.2.2", 8080)
```

### Test Data Management
- Each test creates isolated test data
- `BaseFirebaseTest` handles cleanup between tests
- Test users have predictable IDs and matching codes
- Firestore collections are cleared after each test

## Test Scenarios

### Authentication Flow Tests
- First-time user sign-in and profile creation
- Returning user authentication and data sync
- Authentication state management
- Error handling for invalid credentials
- Unique matching code generation

### Connection Establishment Tests
- Valid matching code connection
- Invalid matching code error handling
- Bidirectional connection updates
- Partner data loading and synchronization
- Connection state management
- Real-time connection status updates

### Transaction System Tests
- Point giving with balance updates
- Point deduction with negative balance support
- Transaction history with real-time updates
- Atomic transaction operations
- Transaction validation and constraints
- Notification delivery for all transaction types
- Concurrent transaction handling

### Timeout System Tests
- Daily timeout limit validation (once per day)
- 30-minute timeout duration and countdown
- Transaction disabling during active timeouts
- Timeout status synchronization between partners
- Automatic timeout expiration
- Timeout history tracking
- Daily reset functionality

### Couple Feature Integration Tests
- Complete conflict resolution workflow
- Point deduction for relationship conflicts
- Timeout system for cooling down during arguments
- Real-time updates for couple interactions
- Negative balance scenarios
- Recovery after conflicts

## Test Reports

After running tests, reports are available at:
- **HTML Report**: `app/build/reports/androidTests/connected/index.html`
- **XML Results**: `app/build/outputs/androidTest-results/connected/`

## Debugging Tests

### Common Issues
1. **Emulator Connection**: Ensure Firebase emulators are running
2. **Device Connection**: Verify Android device/emulator is connected
3. **Network Issues**: Check emulator network configuration (10.0.2.2)
4. **Test Data**: Tests create and clean up their own data

### Logging
Tests include detailed assertions and error messages. Check logcat for additional debugging information:
```bash
adb logcat | grep -E "(BrowniePoints|Firebase|Test)"
```

## Continuous Integration

These tests are designed to run in CI/CD environments:
- Firebase emulators can be started programmatically
- Tests are isolated and don't require external dependencies
- All test data is created and cleaned up automatically
- Tests provide comprehensive coverage of all requirements

## Contributing

When adding new features:
1. Add repository integration tests for new data operations
2. Add end-to-end tests for complete user workflows
3. Update the test suite to include new test classes
4. Ensure all requirements are covered with specific test scenarios
5. Update this README with new test coverage information