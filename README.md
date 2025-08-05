# KSL App Codebase Analysis

This document provides a detailed analysis of the KSL (Kenya Sign Language) Android application codebase, covering its architecture, key components, and code quality.

## Core Architecture

The application follows a clean architecture pattern, leveraging Android Architecture Components (ViewModels, LiveData) and Firebase services for backend functionality. Kotlin Coroutines are extensively used for asynchronous operations, ensuring a responsive UI.

## Key Components and Their Analysis

### `MainActivity.kt`
*   **Purpose:** Main entry point of the application. It sets up the user interface, including the toolbar and bottom navigation, manages fragment navigation, initializes Firebase, and triggers the loading of application settings via `MainViewModel`.
*   **`onCreate`:** Handles the initial setup, Firebase initialization, layout inflation, `Toolbar` and `NavController` configuration, `BottomNavigationView` integration, and initiates settings loading.
*   **`updateToolbarTitle`:** Dynamically updates the toolbar's title based on the currently displayed navigation destination.
*   **Code Quality:** Demonstrates good readability and adheres to Android best practices, utilizing ViewModel delegation, ViewBinding, and string resources. Firebase initialization could be further optimized by moving it to an `Application` class for larger-scale applications.

### `MainViewModel.kt`
*   **Purpose:** Serves as a central hub for managing application settings. It interacts with `SettingsManager` to load and store configurations and exposes these settings to UI components through `LiveData`.
*   **Constructor:** Initializes the `SettingsManager` instance.
*   **`refreshSettingsFromFirestore`:** Orchestrates the loading of settings from Firestore, logs the operation's status, and updates a `_settingsLoaded` `LiveData` to notify observers.
*   **Setters (e.g., `setLanguage`, `setThreshold`):** Delegate the actual setting updates to the `SettingsManager`.
*   **Getters (e.g., `language`, `threshold`):** Provide read-only access to the settings managed by `SettingsManager`.
*   **`resetAllSettings`:** Resets all application settings to their default values via the `SettingsManager`.
*   **Code Quality:** Exhibits excellent architectural design, adhering to the ViewModel pattern. It is highly readable and maintainable, effectively separating UI logic from data management.

### `SettingsManager.kt`
*   **Purpose:** Manages application settings, ensuring persistence through `SharedPreferences` for local storage and `FirebaseFirestore` for cloud synchronization.
*   **`companion object`:** Defines constants for `SharedPreferences` name, Firestore collection/document paths, and keys/default values for all application settings.
*   **`init` block:** Responsible for initializing `FirebaseFirestore` and `FirebaseAuth` instances.
*   **Properties (e.g., `language`, `theme`, `threshold`):** These are custom getters and setters that read from and write to `SharedPreferences`. Importantly, each setter also triggers a `saveToFirestore` call to ensure cloud synchronization.
*   **`saveToFirestore`:** A generic private helper function designed to save a single key-value pair to Firestore for the currently authenticated user.
*   **`loadSettingsFromFirestore`:** Fetches user settings from Firestore. Upon successful retrieval, it updates `SharedPreferences` with the fetched values. If no settings are found in Firestore, it initiates `saveAllSharedPreferencesToFirestore` to push current local settings to the cloud.
*   **`saveAllSharedPreferencesToFirestore`:** Iterates through all entries in `SharedPreferences` and saves them to Firestore.
*   **`resetToDefaults`:** Resets all settings in `SharedPreferences` to their predefined default values and subsequently pushes these defaults to Firestore.
*   **Code Quality:** Demonstrates a strong separation of concerns, effectively handling both local and remote data persistence. The interconnectedness of settings, such as `threshold` updating `handDetectionConfidence`, is well-managed. Includes robust error handling for Firebase initialization.

### `GestureRecognizerHelper.kt`
*   **Purpose:** Encapsulates the logic for MediaPipe Gesture Recognition. It handles model loading, inference execution, and result callbacks across various input types, including live streams, video files, and static images.
*   **Constructor:** Accepts parameters for hand detection/tracking confidence, delegate (CPU/GPU), running mode, context, and a listener for recognition results and errors.
*   **`setupGestureRecognizer`:** Initializes the `GestureRecognizer` with specified options, including the model path, delegate, confidence thresholds, and running mode. It also configures result and error listeners for live stream mode.
*   **`clearGestureRecognizer`:** Properly closes and nullifies the `GestureRecognizer` instance to release resources.
*   **`recognizeLiveStream`:** Processes `ImageProxy` objects from CameraX, converts them into `MPImage` format, applies necessary rotation and flipping, and feeds them to the `recognizeAsync` method for live processing.
*   **`recognizeAsync`:** Calls the asynchronous recognition method on the `GestureRecognizer` for live stream data.
*   **`recognizeVideoFile`:** Manages video file input, extracts frames at specified intervals, converts them to `MPImage`, and runs `recognizeForVideo`. Returns a `ResultBundle` containing the recognition results.
*   **`recognizeImage`:** Processes a static `Bitmap` image, converts it to `MPImage`, and runs `recognize`. Returns a `ResultBundle`.
*   **`returnLivestreamResult` / `returnLivestreamError`:** Callback methods that forward live stream recognition results and errors to the `GestureRecognizerListener`.
*   **`ResultBundle` data class:** A data class designed to hold recognition results, inference time, and the dimensions of the input image.
*   **`GestureRecognizerListener` interface:** Defines callback methods for `onResults` (successful recognition) and `onError` (recognition errors).
*   **Code Quality:** Features a well-structured design with clear separation of concerns for different recognition modes. It effectively utilizes the MediaPipe tasks API and includes robust error handling for recognizer initialization.

### `SubmissionHistoryAdapter.kt`
*   **Purpose:** A `RecyclerView.Adapter` responsible for displaying a list of `TrainingImageItem` objects, which represent user-submitted training data. It includes functionality for asynchronously loading images from URLs.
*   **`onCreateViewHolder`:** Inflates the `item_submission_history.xml` layout for each item in the list.
*   **`onBindViewHolder`:** Binds the data from a `TrainingImageItem` object to the corresponding `ViewHolder`.
*   **`ViewHolder` inner class:**
    *   Holds references to the UI elements (ImageView, TextViews, ImageButton) within each list item.
    *   The `bind` method sets text content, formats timestamps, and initiates the asynchronous image loading process.
    *   `loadImageFromUrl` utilizes `LifecycleCoroutineScope` and `Dispatchers.IO` to load images from URLs on a background thread, then updates the `ImageView` on the main thread. It also includes error handling for image loading failures.
*   **`DiffCallback` inner class:** Implements `DiffUtil.ItemCallback` to enable efficient updates to the `RecyclerView` list, minimizing UI redraws.
*   **Code Quality:** Excellent use of `ListAdapter` and `DiffUtil` for optimized performance. The asynchronous image loading with coroutines is implemented effectively and robustly.

### `ValidationHistoryAdapter.kt`
*   **Purpose:** A `RecyclerView.Adapter` designed to display a list of `ValidationRecord` objects, representing the history of model validations. Similar to `SubmissionHistoryAdapter`, it handles asynchronous image loading.
*   **`onCreateViewHolder`:** Inflates the `item_validation_history.xml` layout for each item.
*   **`onBindViewHolder`:** Binds the data from a `ValidationRecord` object to the `ViewHolder`.
*   **`ViewHolder` inner class:**
    *   Holds references to the UI elements within each list item.
    *   The `bind` method sets text for expected/predicted labels, confidence scores, inference times, and timestamps. It also displays a visual indicator (check/cross) for prediction correctness and loads the associated image.
    *   `loadImageFromUrl` is functionally identical to the one found in `SubmissionHistoryAdapter`.
*   **`DiffCallback` inner class:** Implements `DiffUtil.ItemCallback` for efficient `RecyclerView` updates.
*   **Code Quality:** Similar to `SubmissionHistoryAdapter`, this adapter is well-structured and adheres to best practices for `RecyclerView` and asynchronous operations, ensuring smooth performance.

### `TrainingImage.kt`
*   **Purpose:** This file appears to be a placeholder or an unused file, as it is currently empty.

### `TrainingImageItem.kt`
*   **Purpose:** A data class representing a single training image submission.
*   **Properties:** Includes `id`, `userId`, `imageUrl`, `label`, `validated`, `modelId`, and `timestamp`.
*   **Code Quality:** A standard data class with clearly defined properties, suitable for direct mapping to Firestore documents.

### `User.kt`
*   **Purpose:** A data class representing a user profile within the KSL application.
*   **Properties:** Contains `userId`, `email`, `role`, `profileImageUrl`, `createdAt`, `lastLoginDate`, `isActive`, and `displayName`.
*   **`toMap()`:** Converts a `User` object into a `Map<String, Any>`, facilitating easy storage in Firestore.
*   **`companion object` with `fromMap()`:** Provides a static factory method to construct a `User` object from a Firestore `Map`.
*   **Code Quality:** A standard data class enhanced with convenient conversion methods for seamless integration with Firestore.

### `UserRole.kt`
*   **Purpose:** An enum class that defines the different user roles within the KSL application: `SIGNER`, `DEVELOPER`, and `VALIDATOR`.
*   **`fromString()`:** A static factory method that converts a string representation into a `UserRole` enum, with a default fallback to `SIGNER` for unrecognized roles.
*   **Helper methods (e.g., `canAccessValidation`, `canSubmitData`, `isDeveloper`, `canValidate`):** These methods provide convenient ways to check user permissions based on their assigned role, enabling role-based access control throughout the application.
*   **Code Quality:** A well-defined enum with practical utility methods that support robust role-based access control.

### `ValidationRecord.kt`
*   **Purpose:** A data class representing a single record of model validation.
*   **Properties:** Includes `id`, `userId`, `imageUrl`, `expectedLabel`, `predictedLabel`, `confidenceScore`, `inferenceTimeMs`, `isCorrect`, `modelVersion`, `deviceInfo`, and `timestamp`.
*   **Code Quality:** A standard data class with clearly defined properties, suitable for direct mapping to Firestore documents.

### `LoginActivity.kt`
*   **Purpose:** Manages user authentication processes, including login, a placeholder for Google sign-in, and navigation to registration or forgot password functionalities.
*   **`onCreate`:** Initializes Firebase Authentication and checks for an existing signed-in user to facilitate direct navigation to `MainActivity`.
*   **`setupListeners`:** Configures click listeners for the login button, Google sign-in (with a toast placeholder), registration navigation, and forgot password handling.
*   **`validateInputs`:** Performs client-side validation on email and password fields to ensure data integrity before submission.
*   **`performLogin`:** Authenticates the user with Firebase using their email and password.
*   **`handleForgotPassword`:** Initiates the process of sending a password reset email to the user's registered email address.
*   **`navigateToMainActivity`:** Navigates to the `MainActivity` and clears the activity stack to prevent back navigation to the login screen.
*   **`showLoadingDialog` / `dismissLoadingDialog`:** Manages the display and dismissal of a loading `AlertDialog` during authentication processes.
*   **Code Quality:** Exhibits a clear separation of concerns, with distinct methods for each logical operation. It includes robust input validation and comprehensive error handling. The use of `AlertDialog` for loading indicators is a good practice for user experience.

### `OverlayView.kt`
*   **Purpose:** A custom `View` designed to draw MediaPipe hand landmarks and their connections directly on top of a camera preview, providing a visual representation of detected gestures.
*   **`initPaints`:** Initializes `Paint` objects with specific colors, stroke widths, and styles for drawing lines and points.
*   **`clear`:** Resets the drawing canvas and re-initializes the `Paint` objects, effectively clearing any previously drawn landmarks.
*   **`draw`:** Overrides the standard `draw` method to render the hand landmarks and their connections based on the provided `GestureRecognizerResult`.
*   **`setResults`:** Updates the `GestureRecognizerResult` data and calculates a `scaleFactor` to accurately position and scale the landmarks to match the dimensions of the input image and the current running mode.
*   **Code Quality:** Encapsulates the drawing logic effectively, ensuring clean and maintainable code. It correctly handles scaling for different running modes, which is crucial for accurate visual feedback.

### `RegisterActivity.kt`
*   **Purpose:** Manages the user registration process, including Firebase authentication, updating user profiles, and assigning default roles.
*   **`onCreate`:** Initializes Firebase Authentication.
*   **`setupListeners`:** Configures click listeners for the registration button, navigation back to the login screen, and a placeholder for terms and conditions.
*   **`validateInputs`:** Performs client-side validation for various input fields such as full name, email, phone, password, and ensures agreement to terms and conditions.
*   **`performRegistration`:** Creates a new user account with Firebase, updates the user's display name, and automatically assigns a default `SIGNER` role using the `AccessControlService`.
*   **`showLoadingDialog` / `dismissLoadingDialog`:** Manages the display and dismissal of a loading `AlertDialog` during the registration process.
*   **`assignUserRole`:** Calls the `AccessControlService` to assign the appropriate role to the newly registered user.
*   **Code Quality:** Features comprehensive input validation and robust handling of Firebase user creation and profile updates. Its integration with `AccessControlService` for role management is a key strength.

### `AccountSecurityFragment.kt`
*   **Purpose:** Provides a dedicated interface for managing account security features, including password management, two-factor authentication (currently a placeholder), email verification, and session control.
*   **`onCreateView` / `onViewCreated`:** Initializes Firebase Authentication and Firestore instances, loads existing security information, and sets up action listeners for various security operations.
*   **`loadSecurityInfo`:** Fetches security-related data, such as last password change date, active sessions, and 2FA status, from Firestore.
*   **`updateSecurityUI`:** Updates the user interface to reflect the loaded security information, including a password strength indicator and email verification status.
*   **`updatePasswordStrengthIndicator`:** Calculates and displays a visual indicator of password strength based on its age.
*   **`setupSecurityActions`:** Configures listeners for actions like changing passwords, sending password reset emails, toggling 2FA, verifying email, viewing active sessions, and performing a security checkup.
*   **`showChangePasswordDialog` / `changeUserPassword`:** Manages the dialog and logic for changing a user's password, including re-authentication for security.
*   **`sendPasswordResetEmail` / `sendEmailVerification`:** Sends Firebase-generated password reset and email verification emails.
*   **`performSecurityCheckup`:** Executes a basic security audit of the user's account and highlights any identified issues.
*   **`updateSecurityInfo`:** Updates security-related information in Firestore.
*   **Code Quality:** Effectively utilizes Firebase Authentication and Firestore for secure operations. It offers a comprehensive suite of security features and employs coroutines for efficient asynchronous processing.

### `CameraFragment.kt`
*   **Purpose:** Displays a live camera feed, performs real-time gesture recognition using MediaPipe, and translates recognized signs into natural language text using the Gemini API. It supports two distinct modes: "Word Recognition" and "Live Translation."
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, `GestureRecognizerHelper`, `GeminiService`, and `TextToSpeech` engine, and sets up the camera.
*   **`setupNewControls`:** Configures UI switches for mode selection (Word Recognition/Live Translation) and Text-to-Speech (TTS), along with a button for switching camera lenses.
*   **`updateUiModeText`:** Updates the text displayed on the UI to reflect the current operating mode.
*   **`clearRecognizedText`:** Clears all accumulated recognized words and input for the Large Language Model (LLM).
*   **`startLlmTimer` / `updateLlmTimerDisplay`:** Manages a countdown timer that collects recognized words before sending them for LLM processing in "Live Translation" mode.
*   **`processLlmTranslation`:** Sends the collected signs to the `GeminiService` for translation and, if TTS is enabled, speaks the translated result.
*   **`initTextToSpeech` / `speakText`:** Initializes and utilizes the `TextToSpeech` engine for audio output.
*   **`startInactivityTimer` / `resetInactivityTimer`:** Manages a timer that clears recognized text after a period of user inactivity.
*   **`setUpCamera` / `bindCameraUseCases`:** Configures CameraX for live camera preview and image analysis.
*   **`onResults` (GestureRecognizerHelper.GestureRecognizerListener):** This callback processes gesture recognition results, handling both single-handed and two-handed gestures, updating the UI, and feeding words into the appropriate mode's logic.
*   **`processSingleHandedGesture` / `processTwoHandedGesture`:** Contains the core logic for accumulating and processing recognized words based on the active UI mode.
*   **Code Quality:** A complex but well-structured component that seamlessly integrates MediaPipe, CameraX, the Gemini API, and TTS. It makes effective use of coroutines for background tasks and ensures UI updates are performed on the main thread, contributing to a smooth user experience.

### `GalleryFragment.kt`
*   **Purpose:** Enables users to select images or videos from their device's gallery, perform gesture recognition on the selected media, and display the recognition results. It also includes foundational (placeholder) functions for interacting with Firebase Storage and Firestore for cloud gallery features (upload, download, delete, update, fetch).
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, `GestureRecognizerHelper`, and sets up the content selection mechanism.
*   **`getContent` (ActivityResultContracts.OpenDocument):** This callback handles the URI of the selected media, determines its type (image or video), and invokes the appropriate recognition function.
*   **`initBottomSheetControls`:** Configures listeners for adjusting confidence thresholds and selecting the delegate (CPU/GPU) within a bottom sheet UI.
*   **`updateControlsUi`:** Updates the UI elements within the bottom sheet and resets the gesture recognizer to its initial state.
*   **`runGestureRecognitionOnImage` / `runGestureRecognitionOnVideo`:** Loads the selected media, initializes `GestureRecognizerHelper` in either `IMAGE` or `VIDEO` mode, and executes the recognition process.
*   **`displayVideoResult`:** Manages the display of video recognition results frame by frame.
*   **`updateDisplayView` / `loadMediaType`:** Controls the visibility of image/video views and identifies the type of media selected.
*   **`setUiEnabled`:** Toggles the enabled state of UI elements during media processing to prevent user interaction.
*   **`onError` (GestureRecognizerHelper.GestureRecognizerListener):** Handles and reports errors originating from the gesture recognizer.
*   **Firebase Placeholder Functions:** `uploadImageToFirebase`, `uploadVideoToFirebase`, `saveFileInfoToFirestore`, `fetchGalleryFromFirestore`, `deleteFileFromFirebase`, `updateFileInFirebase`, `downloadFileFromFirebase`. These functions are currently not fully integrated into the main application flow but provide a solid foundation for future cloud gallery functionalities.
*   **Code Quality:** Demonstrates a clear separation of concerns for image and video processing. It effectively utilizes MediaPipe for recognition. The Firebase functions are well-defined as modular placeholders, indicating a forward-thinking design.

### `GestureRecognizerResultsAdapter.kt`
*   **Purpose:** A `RecyclerView.Adapter` designed to display the results of gesture recognition, specifically the recognized labels and their corresponding confidence scores.
*   **`updateResults`:** Updates the adapter's internal data with new `GestureRecognizerResult` or a `List<Category>`, sorts the results by score, and notifies the `RecyclerView` to refresh its display.
*   **`updateAdapterSize`:** Sets the maximum number of recognition results to be displayed by the adapter.
*   **`onCreateViewHolder` / `onBindViewHolder`:** Standard `RecyclerView` adapter methods responsible for creating new `ViewHolder` instances and binding data to them.
*   **`ViewHolder` inner class:** Binds the recognized label and its score to `TextViews` within each list item.
*   **Code Quality:** A standard and efficient `RecyclerView` adapter implementation. It effectively updates results, contributing to a smooth and responsive user interface.

### `LearnKslFragment.kt`
*   **Purpose:** Serves as a landing page for users to learn Kenya Sign Language (KSL). It features sections dedicated to learning the alphabet, numbers, and common phrases, along with a direct link to a practice mode.
*   **`onCreateView` / `onViewCreated`:** Inflates the fragment's layout and sets up click listeners for navigating to the various learning sections and the practice screen.
*   **Code Quality:** A simple and straightforward UI fragment, primarily focused on navigation. Its design is clear and intuitive for users.

### `ModelValidationFragment.kt`
*   **Purpose:** Allows authorized users (specifically `VALIDATOR` and `DEVELOPER` roles) to validate the KSL recognition model. This involves capturing images, analyzing the model's predictions, and submitting the validation data to Firebase for further analysis and improvement.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase services, `AccessControlService`, and sets up the camera. It includes a crucial role-based access check to restrict functionality.
*   **`initializeValidationFeature`:** Configures Firebase, `GestureRecognizerHelper`, and the camera flip button.
*   **`setupButtonListeners`:** Sets up click listeners for the capture, validate, and view history buttons.
*   **`captureImage`:** Utilizes CameraX to capture an image and saves it to the device's local storage.
*   **`analyzeAndValidateImage`:** Loads the captured image, runs `GestureRecognizerHelper` in `IMAGE` mode, measures the inference time, and displays the prediction results.
*   **`updateMetricsDisplay`:** Updates the UI with the confidence score, inference time, and the model's prediction.
*   **`handleEmptyPrediction` / `handlePredictionError`:** Manages scenarios where no sign is identified in the image or an error occurs during the analysis.
*   **`uploadValidationData`:** Uploads the captured image to Firebase Storage and saves the associated validation metadata to Firestore.
*   **`saveValidationDataToFirestore`:** Creates a `ValidationRecord` object and persists it to Firestore.
*   **`resetUIAfterSubmissionAttempt` / `resetCaptureState`:** Resets the UI elements after a submission attempt or to prepare for a new image capture.
*   **`setUpCamera` / `bindCameraUseCases`:** Configures CameraX for image capture functionality.
*   **`onError` / `onResults` (GestureRecognizerHelper.GestureRecognizerListener):** Implements the listener interface to receive and process gesture recognition results and errors.
*   **Code Quality:** A robust implementation for model validation, demonstrating strong integration of CameraX, MediaPipe, Firebase Storage, and Firestore. It features stringent access control, comprehensive error handling, and effective UI state management.

### `PermissionsFragment.kt`
*   **Purpose:** Responsible for requesting necessary runtime permissions (e.g., camera access) from the user. Once the required permissions are granted, it navigates the user to the camera fragment.
*   **`requestPermissionLauncher`:** An `ActivityResultLauncher` specifically configured for requesting the camera permission.
*   **`onCreate`:** Checks the current status of the camera permission. If not granted, it initiates the permission request; otherwise, it directly navigates to the camera fragment.
*   **`navigateToCamera`:** Navigates the user to the `CameraFragment`.
*   **`hasPermissions`:** A static helper method that provides a convenient way to check if all permissions required by the application have been granted.
*   **Code Quality:** Implements standard Android permission handling practices, ensuring a smooth and compliant user experience regarding app permissions.

### `ProfileFragment.kt`
*   **Purpose:** Provides a comprehensive user profile management interface. It displays detailed user information and allows for profile editing, user sign-out, and includes a placeholder for account deletion functionality.
*   **`onCreateView` / `onViewCreated`:** Initializes Firebase Authentication and Firestore, the `AccessControlService`, and initiates the loading of the user's profile.
*   **`loadUserProfile`:** Fetches the current user's profile data from Firestore.
*   **`updateProfileUI`:** Updates the user interface elements with the user's display name, email, role, account creation and last login dates, and account status.
*   **`setupClickListeners`:** Configures click listeners for various profile actions, including editing the profile, navigating to preferences (placeholder), security settings (placeholder), signing out, and initiating account deletion.
*   **`showEditProfileDialog` / `updateUserProfile`:** Manages the dialog for editing the user's display name and saving the changes back to Firestore.
*   **`showSignOutConfirmation` / `performSignOut`:** Handles the user sign-out process, including a confirmation dialog, and navigates to the `LoginActivity`.
*   **`showDeleteAccountConfirmation` / `showFinalDeleteConfirmation`:** Provides placeholder functionality for account deletion, including multiple confirmation steps and warnings.
*   **Code Quality:** A well-structured component for profile management. It effectively integrates with Firebase Authentication and Firestore, and makes good use of `AlertDialog` for user confirmations, enhancing the user experience.

### `SettingsFragment.kt`
*   **Purpose:** Serves as a centralized hub for managing various application settings. This includes general settings like language and theme, ML model parameters, and provides links to more detailed profile, security, and user preferences sections.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase instances, `AccessControlService`, loads the current user's profile, and observes settings changes from the `MainViewModel`.
*   **`loadCurrentUserProfile` / `updateProfileUI`:** Loads and displays the current user's profile information.
*   **`populateUiFromViewModel`:** Populates the UI elements with the current settings retrieved from the `MainViewModel`.
*   **`setupSettingsClickListeners`:** Configures listeners for general settings such as language selection, theme selection, threshold adjustment (via a seekbar), and ML model download.
*   **`showLanguageSelectionDialog` / `applyLanguageChange`:** Manages the dialog for language selection and applies the chosen language.
*   **`showThemeSelectionDialog` / `applyThemeChange`:** Manages the dialog for theme selection and applies the chosen theme.
*   **`showPasswordResetConfirmDialog` / `sendPasswordResetEmail`:** Provides functionality for users to reset their password via email.
*   **`setupAboutSection` / `openPrivacyPolicy` / `showAboutDialog`:** Manages the "About" section, including a link to the privacy policy and an application information dialog.
*   **`downloadModelsAndUpdateViewModel`:** Simulates the download of ML models and updates the `MainViewModel` accordingly.
*   **`setupBottomSheetListeners`:** A placeholder for bottom sheet controls; many UI elements are commented out as they are not present in the current layout.
*   **`calculateStorageUsage` / `getDirSize` / `clearAppCache` / `deleteDir`:** Utility functions for cache management, though some are partially commented out due to missing UI elements.
*   **`performLogout` / `setupLogoutButton`:** Handles the user logout process.
*   **`setupProfileManagement` / `setupAccountSettings` / `setupSecurityFeatures` / `setupUserPreferences`:** These methods are designed to navigate to dedicated fragments for comprehensive management of profile, account, security, and user preferences, or provide local dialogs as fallbacks.
*   **Code Quality:** A well-organized and centralized component for managing application settings. It effectively uses `MainViewModel` for shared settings and handles various types of configurations. The presence of commented-out sections indicates a design that anticipates future UI expansions.

### `SubmissionHistoryFragment.kt`
*   **Purpose:** Displays a user's historical record of submitted training images. Users can view their past submissions and delete specific entries.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase instances, and sets up the `RecyclerView` for displaying the history.
*   **`setupRecyclerView`:** Configures the `SubmissionHistoryAdapter`, including a callback for handling item deletion.
*   **`loadSubmissionHistory`:** Fetches `TrainingImageItem` records from Firestore for the current user, ordered by timestamp in descending order.
*   **`handleNoUser` / `handleEmptyHistory` / `handleSuccessfulLoad` / `handleLoadFailure`:** Manages the UI state (e.g., showing "No history" message, progress bar) based on the outcome of data loading.
*   **`updateSummaryReport`:** Calculates and displays a summary of the user's submissions, such as total images and unique signs.
*   **`confirmDeleteItem` / `deleteSubmission`:** Handles the process of deleting a submission, which involves removing the entry from both Firestore and Firebase Storage.
*   **`handleDeleteSuccess` / `handleImageDeleteFailure` / `handleDocumentDeleteFailure`:** Provides feedback to the user regarding the success or failure of deletion operations.
*   **Code Quality:** A well-implemented component for displaying and managing submission history. It utilizes `ListAdapter` and `DiffUtil` for efficient `RecyclerView` updates and includes robust error handling for Firebase operations.

### `SubmitTrainingDataFragment.kt`
*   **Purpose:** Enables users to capture images of signs, categorize them, and submit them as training data to Firebase. This fragment includes features for image quality validation and batch uploading.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase instances, `AccessControlService`, and sets up the camera.
*   **`setupCategorySpinner` / `updateSignSpinner`:** Configures spinners for selecting sign categories (e.g., Alphabet, Numbers) and specific signs within those categories.
*   **`setupButtonListeners`:** Sets up click listeners for image capture, data submission (single or batch), clearing the batch, and viewing submission history.
*   **`takePicture`:** Uses CameraX to capture an image, saves it locally, and automatically triggers an image quality validation.
*   **`addToBatch`:** Adds a captured image to a batch after it passes quality validation.
*   **`clearBatchImages`:** Clears all images currently in the batch.
*   **`startBatchUpload` / `performBatchUpload` / `uploadSingleImage`:** Manages the entire batch upload process, including progress tracking and individual image uploads to Firebase Storage and Firestore.
*   **`performQualityCheck` / `calculateBlurScore` / `Bitmap.isBrightEnough`:** Implements a comprehensive image quality validation system, checking for file size, dimensions, brightness, and blur.
*   **`updateBatchUI` / `updateProgressUI` / `resetProgressUI` / `updateUploadStatus` / `updateQualityUI`:** Manages UI updates related to the batch, upload progress, and image quality status.
*   **`resetCaptureState` / `resetUiAfterSubmissionAttempt`:** Resets the UI elements after an image capture or submission attempt.
*   **`uploadImageAndSubmitData`:** Handles the process of uploading a single image and submitting its metadata to Firestore.
*   **`validateImageQuality`:** Performs quality checks on an image before it is uploaded.
*   **`saveTrainingDataToFirestore`:** Saves the training data metadata to Firestore.
*   **`setUpCamera` / `bindCameraUseCases`:** Configures CameraX for image capture.
*   **Code Quality:** A feature-rich component for data submission, with a strong emphasis on image quality validation and efficient batch processing. It effectively utilizes Firebase services and coroutines.

### `UserPreferencesFragment.kt`
*   **Purpose:** Manages user-specific preferences and advanced settings within the application. This includes notification settings, privacy preferences, and various app customization options.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase instances, and loads the user's preferences.
*   **`loadUserPreferences`:** Fetches user preferences from Firestore. If no preferences are found, it applies default values.
*   **`updatePreferencesUI`:** Updates the UI switches and other elements to reflect the current preference values, also managing the visibility of dependent settings.
*   **`setupPreferenceListeners`:** Configures listeners for all preference switches. Changes trigger `saveUserPreferences`. Includes a warning dialog for disabling data collection.
*   **`updateDependentSettings`:** Adjusts the UI based on inter-dependent preferences (e.g., auto-sync might be disabled if offline mode is enabled).
*   **`saveUserPreferences`:** Persists any changes to user preferences back to Firestore.
*   **`showDataCollectionWarning` / `showClearCacheConfirmation` / `showResetPreferencesConfirmation`:** Displays confirmation dialogs for sensitive actions like disabling data collection, clearing cache, or resetting preferences.
*   **`performClearCache` / `resetToDefaults`:** Implements the logic for clearing the application cache and resetting all preferences to their default values.
*   **Code Quality:** A comprehensive component for managing user preferences. It effectively uses Firebase Firestore for data persistence and `AlertDialog` for user confirmations, enhancing the user experience.

### `ValidationHistoryFragment.kt`
*   **Purpose:** Displays a user's historical record of model validation. Users can view past validation attempts and delete specific records.
*   **`onCreateView` / `onViewCreated`:** Initializes the UI, Firebase instances, and sets up the `RecyclerView` for displaying the validation history.
*   **`setupRecyclerView`:** Configures the `ValidationHistoryAdapter`, including a callback for handling item deletion.
*   **`loadValidationHistory`:** Fetches `ValidationRecord` records from Firestore for the current user, ordered by timestamp in descending order.
*   **`handleNoUser` / `handleEmptyHistory` / `handleSuccessfulLoad` / `handleLoadFailure`:** Manages the UI state (e.g., showing "No history" message, progress bar) based on the outcome of data loading.
*   **`updateSummaryReport`:** Calculates and displays a summary of the model's validation performance, including accuracy, total validations, and unique signs.
*   **`confirmDeleteRecord` / `deleteRecord`:** Handles the process of deleting a validation record, which involves removing the entry from both Firestore and Firebase Storage.
*   **`handleDeleteSuccess` / `handleImageDeleteFailure` / `handleDocumentDeleteFailure`:** Provides feedback to the user regarding the success or failure of deletion operations.
*   **Code Quality:** A well-implemented component for displaying and managing validation history. It utilizes `ListAdapter` and `DiffUtil` for efficient `RecyclerView` updates and includes robust error handling for Firebase operations.

### `AccessControlService.kt`
*   **Purpose:** Provides a service layer for managing user authentication, roles, and role-based access control within the application.
*   **`getCurrentUser`:** Retrieves the current authenticated user's profile from Firestore. If a profile doesn't exist, it creates a default one.
*   **`saveUserProfile`:** Saves a `User` object to Firestore.
*   **`canAccessValidation` / `canSubmitData` / `isDeveloper`:** Checks if the current user possesses specific permissions based on their `UserRole`.
*   **`updateUserRole`:** Allows a developer to change another user's role, with a built-in check to ensure the current user has developer permissions.
*   **`assignRoleToUser`:** Assigns a role to a user, creating their profile in Firestore if it doesn't already exist.
*   **`checkSubmissionAccess`:** Provides a callback-based method to verify if a user has permission to submit training data (currently, all authenticated users are allowed).
*   **`getUserById`:** Retrieves a user profile from Firestore using their user ID.
*   **Code Quality:** Centralizes access control logic, making it robust and maintainable. It effectively uses Firebase Authentication and Firestore, and leverages Kotlin coroutines for asynchronous operations.

### `GeminiService.kt`
*   **Purpose:** Facilitates interaction with the Google Gemini API to translate recognized sign language gestures into natural language text.
*   **`companion object`:** Defines constants for the Gemini API URL and the API key.
*   **`client` (OkHttpClient):** An HTTP client used for making network requests to the Gemini API.
*   **`translateSignsToText`:**
    *   Takes a list of recognized sign language gestures as input.
    *   Constructs a detailed prompt for the Gemini API, instructing it to act as a sign language interpreter and convert the individual signs into a fluent English sentence.
    *   Builds a JSON request body containing the prompt and generation configuration parameters.
    *   Sends an HTTP POST request to the Gemini API endpoint.
    *   Parses the JSON response to extract the translated text.
    *   Includes comprehensive error handling for API requests and response parsing.
*   **Code Quality:** Encapsulates the API interaction logic effectively. It uses OkHttp for network communication and JSON for request/response formatting. The prompt is well-designed to guide the Gemini API for accurate translation. Includes good error logging for debugging.

## Overall Code Quality

The codebase is generally well-structured, adhering to modern Android development best practices. The consistent use of Android Architecture Components (ViewModels, LiveData) and Kotlin Coroutines contributes to a robust and responsive application. The clear separation of concerns into distinct fragments, adapters, models, and services enhances maintainability and readability. Firebase services (Authentication, Firestore, Storage) are effectively integrated across various features, providing a scalable and reliable backend. While some UI elements are noted as placeholders or commented out, indicating areas for future expansion, the core logic and architectural patterns are solid.