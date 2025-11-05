# Requirements Document

## Introduction

The Brownie Points App is a relationship management Android application designed specifically for couples to manage their interactions through a point-based system. The app facilitates both positive reinforcement and conflict resolution by allowing partners to give brownie points for good deeds and appreciation, deduct points during disagreements, and request timeout breaks during arguments. This comprehensive approach helps couples track their relationship dynamics and provides tools for healthy conflict management. Users connect with each other using unique matching codes to create secure, private connections.

## Requirements

### Requirement 1

**User Story:** As a user, I want to sign in with my Google account and set up my profile, so that I can start using the app to exchange brownie points with someone special.

#### Acceptance Criteria

1. WHEN a user opens the app for the first time THEN the system SHALL display a Google Sign-In screen using Firebase Authentication
2. WHEN a user successfully signs in with Google THEN the system SHALL create their profile in Firebase Firestore with their Google account information
3. WHEN a user completes sign-in THEN the system SHALL generate a unique matching code and store it in Firestore
4. IF a user has already signed in THEN the system SHALL automatically authenticate them and display the main interface
5. WHEN a user signs in THEN the system SHALL sync their profile data from Firestore

### Requirement 2

**User Story:** As a user, I want to connect with another person using a matching code or by adding them as a friend, so that we can start exchanging brownie points.

#### Acceptance Criteria

1. WHEN a user wants to connect with someone THEN the system SHALL provide options to either enter a matching code or add a friend
2. WHEN a user enters a valid matching code THEN the system SHALL query Firestore to find the corresponding user and establish a connection
3. WHEN a user successfully connects with another person THEN the system SHALL update both users' connection status in Firestore and enable point exchange
4. IF a user enters an invalid matching code THEN the system SHALL display an error message and allow retry
5. WHEN two users are connected THEN the system SHALL store this relationship in Firestore with real-time synchronization

### Requirement 3

**User Story:** As a user, I want to give brownie points to my connected partner, so that I can reward them for good deeds or show appreciation.

#### Acceptance Criteria

1. WHEN a user wants to give points THEN the system SHALL display a point-giving interface with the connected partner's profile
2. WHEN a user selects an amount of points to give THEN the system SHALL allow values between 1 and 10 points per transaction
3. WHEN a user gives points THEN the system SHALL update both users' point balances in Firestore using atomic transactions
4. WHEN points are given THEN the system SHALL create a transaction record in Firestore with timestamp, sender, receiver, and points
5. IF a user tries to give points without a connected partner THEN the system SHALL display a message to connect with someone first

### Requirement 4

**User Story:** As a user, I want to optionally include a message when giving brownie points, so that I can explain why I'm rewarding my partner or add a personal touch.

#### Acceptance Criteria

1. WHEN a user is giving points THEN the system SHALL provide an optional text field for adding a message
2. WHEN a user enters a message THEN the system SHALL limit the message to 200 characters
3. WHEN points are given with a message THEN the system SHALL store the message with the transaction in Firestore
4. WHEN a user receives points with a message THEN the system SHALL display both the points and the message using Firebase Cloud Messaging for notifications

### Requirement 5

**User Story:** As a user, I want to view my current brownie point balance and transaction history, so that I can track the points I've given and received over time.

#### Acceptance Criteria

1. WHEN a user opens the main screen THEN the system SHALL display their current point balance from Firestore prominently
2. WHEN a user wants to view history THEN the system SHALL provide access to a transaction history screen with real-time Firestore queries
3. WHEN viewing transaction history THEN the system SHALL display all transactions with points, messages, timestamps, and whether points were given or received
4. WHEN displaying transactions THEN the system SHALL sort them by most recent first using Firestore ordering
5. WHEN a user receives points THEN the system SHALL update their balance in real-time using Firestore listeners and show a notification

### Requirement 6

**User Story:** As a user, I want to receive notifications when my partner gives me brownie points, so that I'm immediately aware of their appreciation.

#### Acceptance Criteria

1. WHEN a user receives brownie points THEN the system SHALL display an immediate in-app notification using Firestore real-time listeners
2. WHEN points are received with a message THEN the system SHALL include the message in the notification
3. WHEN a notification is displayed THEN the system SHALL show the sender's name, points received, and any included message
4. WHEN a user taps a notification THEN the system SHALL navigate to the transaction details or main screen
5. IF the app is in the background THEN the system SHALL send a push notification using Firebase Cloud Messaging

### Requirement 7

**User Story:** As a couple user, I want to be able to deduct brownie points from my partner when we have disagreements or fights, so that the point system reflects both positive and negative interactions in our relationship.

#### Acceptance Criteria

1. WHEN a user wants to deduct points THEN the system SHALL provide a "Deduct Points" option alongside the "Give Points" feature
2. WHEN a user selects deduct points THEN the system SHALL allow values between 1 and 10 points per deduction transaction
3. WHEN points are deducted THEN the system SHALL subtract the points from the receiver's total balance using atomic Firestore transactions
4. WHEN points are deducted THEN the system SHALL create a negative transaction record in Firestore with timestamp, sender, receiver, and negative point value
5. WHEN a user deducts points THEN the system SHALL optionally allow adding a reason message (limited to 200 characters)
6. IF deducting points would result in a negative balance THEN the system SHALL allow the balance to go negative
7. WHEN points are deducted THEN the system SHALL send a notification to the receiver about the point deduction

### Requirement 8

**User Story:** As a couple user, I want to request a 30-minute timeout break during arguments, so that we can cool down and avoid escalating conflicts, with this feature limited to once per day per person.

#### Acceptance Criteria

1. WHEN a user wants to take a timeout THEN the system SHALL provide a "Request Timeout" button accessible from the main screen
2. WHEN a user requests a timeout THEN the system SHALL check if they have already used their daily timeout allowance
3. IF a user has not used their daily timeout THEN the system SHALL initiate a 30-minute timeout period and record the timestamp in Firestore
4. WHEN a timeout is active THEN the system SHALL display a countdown timer showing remaining timeout duration
5. WHEN a timeout is active THEN the system SHALL disable point giving/deducting functionality for both users in the connection
6. WHEN a timeout expires THEN the system SHALL automatically re-enable all functionality and send notifications to both users
7. WHEN a timeout is requested THEN the system SHALL immediately notify the partner about the timeout request and duration
8. IF a user tries to request a timeout after already using their daily allowance THEN the system SHALL display a message indicating they must wait until the next day
9. WHEN a new day begins (midnight) THEN the system SHALL reset the timeout allowance for both users
10. WHEN either user is in timeout THEN the system SHALL display the timeout status prominently on both users' main screens

### Requirement 9

**User Story:** As a user, I want the app to work offline and sync when connected, so that I can give points even without internet connectivity.

#### Acceptance Criteria

1. WHEN the app has no internet connection THEN the system SHALL use Firestore offline persistence to cache data locally
2. WHEN internet connectivity is restored THEN the system SHALL automatically sync all offline changes using Firestore's built-in sync
3. WHEN syncing occurs THEN the system SHALL use Firestore's conflict resolution to maintain data consistency
4. IF sync fails THEN the system SHALL use Firestore's automatic retry mechanism and display connection status
5. WHEN offline THEN the system SHALL clearly indicate the offline status using Firestore's network state monitoring