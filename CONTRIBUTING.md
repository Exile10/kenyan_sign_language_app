# Contributing to KSL Translator

Thank you for your interest in contributing to the KSL (Kenyan Sign Language) Translator project. This document provides guidelines and information for contributors.

## Table of Contents

- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)
- [Community Guidelines](#community-guidelines)

## How to Contribute

We welcome several types of contributions:

- **Bug fixes**: Identify and fix issues
- **Feature development**: Add new functionality
- **Training data**: Submit KSL gesture images via the in-app Submit Data feature — this is the highest-impact contribution for model accuracy
- **Documentation**: Improve guides, inline docs, and translations
- **UI/UX**: Improve accessibility and user experience
- **Testing**: Add test cases and improve coverage
- **Swahili localisation**: Translate UI strings in `res/values-sw/`

### Contribution Workflow

1. **Fork the repository**
   ```bash
   git clone https://github.com/jarida-io/kenyan_sign_language_app.git
   cd kenyan_sign_language_app
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes** — follow coding standards below

4. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

5. **Commit and push**
   ```bash
   git add .
   git commit -m "feat: describe your change"
   git push origin feature/your-feature-name
   ```

6. **Open a Pull Request** against `master`

## Development Setup

### Prerequisites

- **Android Studio**: Giraffe (2022.3.1) or later
- **JDK**: 17 or higher
- **Android SDK**: API 24–35
- **Firebase Account**: For Firestore, Auth, and Storage

### Environment Setup

1. Clone the repo (see above)

2. Configure Firebase:
   - Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
   - Enable Authentication (Email/Password), Firestore, and Storage
   - Download `google-services.json` and place it in `app/`

3. Add your Gemini API key to `local.properties`:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
   > The Gemini key is only needed for the natural language translation feature. Gesture recognition runs fully on-device.

4. Build:
   ```bash
   ./gradlew clean build
   ```
   The build task downloads `ksl_first_model.task` from Firebase Storage automatically.

## Coding Standards

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

### Naming Conventions

| Element | Style | Example |
|---|---|---|
| Classes | PascalCase | `GestureRecognizerHelper` |
| Functions | camelCase | `recognizeLiveStream` |
| Properties | camelCase | `isAuthenticated` |
| Constants | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| XML resources | snake_case | `fragment_camera.xml` |

### Architecture Guidelines

- **Fragments**: Handle UI only — no direct database or network calls
- **ViewModels**: Own business logic and state; exposed via LiveData
- **Services**: Specialised logic (auth, validation, ML inference, Gemini)
- **No direct Firestore calls from Fragments**: Go through a Service or ViewModel

### Error Handling

Use `Result<T>` for operations that can fail:

```kotlin
suspend fun getCurrentUser(): Result<User> {
    return try {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure(...)
        val snapshot = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        Result.success(snapshot.toObject(User::class.java)!!)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Commit Message Format

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add Swahili UI translations
fix: correct confidence threshold default value
docs: update MediaPipe configuration guide
test: add GestureRecognizerHelper unit tests
refactor: extract ValidationService from MainViewModel
```

## Testing Requirements

### Test Structure

```
src/test/                     # Unit tests (run on JVM)
src/androidTest/              # Instrumented tests (run on device/emulator)
  └── assets/
      ├── hand_thumb_up.jpg   # Test image for gesture recognition
      └── test_video.mp4      # Test video for gallery analysis
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (connected device or emulator required)
./gradlew connectedAndroidTest

# Specific test class
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jerry.ksl.gesturerecognizer.GestureRecognizerTest
```

### Coverage Goals

- Business logic in Services and ViewModels: unit test coverage expected
- Critical user flows (login, recognition, data submission): instrumented test coverage expected
- New features must include tests before merge

## Pull Request Process

### Before Submitting

1. All tests pass locally
2. New code follows naming and architecture conventions above
3. README updated if you added a user-facing feature or changed setup steps
4. No API keys or credentials committed (use `local.properties` or build config)

### PR Template

```markdown
## What this changes
Brief description of the change and why.

## Type
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation
- [ ] Refactor
- [ ] Tests

## Testing done
Describe how you tested this change.

## Screenshots (UI changes)
```

### Review Criteria

- Does the code work as intended?
- Does it follow MVVM — no direct DB calls from Fragments?
- Are there tests?
- Does it introduce any security issues (credentials, exposed keys)?
- Does it affect ML inference performance?

## Issue Reporting

### Bug Reports

Please include:

- Device model and Android version
- App behaviour vs. expected behaviour
- Steps to reproduce
- Logcat output if available
- Which component is affected (camera / validation / submission / auth)

### Feature Requests

Please describe:

- The problem you are trying to solve
- Your proposed solution
- Any alternatives you have considered

### Security Issues

Do not open a public issue for security vulnerabilities. Email **hello@jarida.io** with details.

## Community Guidelines

- Be respectful and inclusive — contributors come from all backgrounds
- Provide constructive feedback in code reviews
- Label issues helpfully (`good first issue`, `help wanted`, `bug`, `enhancement`)
- If you are new to Android or Kotlin, start with issues labelled `good first issue`

## Contact

- **General questions**: Open a [GitHub Discussion](https://github.com/jarida-io/kenyan_sign_language_app/discussions)
- **Bug reports**: Open a [GitHub Issue](https://github.com/jarida-io/kenyan_sign_language_app/issues)
- **Security concerns**: hello@jarida.io

## License

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
