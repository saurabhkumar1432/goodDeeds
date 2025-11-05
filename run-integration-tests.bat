@echo off
echo ========================================
echo Brownie Points App - Integration Tests
echo ========================================
echo.

echo This script will run the complete integration test suite for the Brownie Points App.
echo.
echo Prerequisites:
echo 1. Firebase CLI installed (npm install -g firebase-tools)
echo 2. Android device/emulator connected
echo 3. Firebase emulators configured
echo.

echo Starting Firebase emulators...
echo.
start /B firebase emulators:start --only auth,firestore

echo Waiting for emulators to start...
timeout /t 10 /nobreak > nul

echo.
echo Running integration tests...
echo.

echo ========================================
echo Running Repository Integration Tests
echo ========================================
call gradlew connectedAndroidTest --tests "com.browniepoints.app.repository.*"

echo.
echo ========================================
echo Running End-to-End Integration Tests
echo ========================================
call gradlew connectedAndroidTest --tests "com.browniepoints.app.*EndToEndTest"

echo.
echo ========================================
echo Running Complete Test Suite
echo ========================================
call gradlew connectedAndroidTest --tests "com.browniepoints.app.FirebaseIntegrationTestSuite"

echo.
echo ========================================
echo Test Execution Complete
echo ========================================
echo.
echo Test Results Summary:
echo - Repository Tests: Individual component testing
echo - End-to-End Tests: Complete workflow testing
echo - Integration Suite: Comprehensive test coverage
echo.
echo Check the test reports in: app/build/reports/androidTests/connected/
echo.

pause