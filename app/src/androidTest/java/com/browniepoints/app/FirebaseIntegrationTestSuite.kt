package com.browniepoints.app

import com.browniepoints.app.repository.AuthRepositoryIntegrationTest
import com.browniepoints.app.repository.ConnectionRepositoryIntegrationTest
import com.browniepoints.app.repository.TransactionRepositoryIntegrationTest
import com.browniepoints.app.repository.UserRepositoryIntegrationTest
import com.browniepoints.app.repository.TimeoutRepositoryIntegrationTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Comprehensive test suite that runs all Firebase integration and end-to-end tests
 * 
 * This suite includes tests for:
 * - Firebase Authentication operations and end-to-end auth flow
 * - Firestore data operations (Users, Transactions, Connections, Timeouts)
 * - Real-time data synchronization
 * - Atomic transaction operations
 * - Complete user workflows and feature integration
 * - Couple-specific features (point deduction, timeout system)
 * 
 * To run these tests with Firebase Emulator:
 * 1. Install Firebase CLI: npm install -g firebase-tools
 * 2. Start emulators: firebase emulators:start --only auth,firestore
 * 3. Run tests: ./gradlew connectedAndroidTest
 * 
 * Test Coverage:
 * - Repository Integration Tests: Test individual repository operations
 * - End-to-End Tests: Test complete user workflows and feature integration
 * - Couple Feature Tests: Test relationship-specific functionality
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Repository Integration Tests
    AuthRepositoryIntegrationTest::class,
    UserRepositoryIntegrationTest::class,
    TransactionRepositoryIntegrationTest::class,
    ConnectionRepositoryIntegrationTest::class,
    TimeoutRepositoryIntegrationTest::class,
    
    // End-to-End Integration Tests
    AuthenticationEndToEndTest::class,
    ConnectionEndToEndTest::class,
    TransactionEndToEndTest::class,
    TimeoutEndToEndTest::class,
    
    // Couple Feature End-to-End Tests
    CoupleFeatureEndToEndTest::class
)
class FirebaseIntegrationTestSuite