# Contributing to KSL Translator

Thank you for your interest in contributing to the KSL (Kenyan Sign Language) Translator project! This document provides guidelines and information for contributors.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)
- [Documentation Guidelines](#documentation-guidelines)
- [Community Guidelines](#community-guidelines)

## 🤝 Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to [conduct@ksltranslator.com](mailto:conduct@ksltranslator.com).

## 🚀 How to Contribute

### Types of Contributions

We welcome several types of contributions:

- **🐛 Bug Reports**: Help us identify and fix issues
- **✨ Feature Requests**: Suggest new functionality
- **🔧 Code Contributions**: Submit bug fixes and new features
- **📚 Documentation**: Improve docs, tutorials, and examples
- **🎨 UI/UX Improvements**: Enhance user experience
- **🧪 Testing**: Add test cases and improve coverage
- **🌐 Translations**: Add support for additional sign languages
- **📊 Training Data**: Contribute sign language datasets

### Contribution Workflow

1. **Fork the Repository**
   ```bash
   git clone https://github.com/yourusername/ksl-translator.git
   cd ksl-translator
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make Your Changes**
   - Follow our coding standards
   - Add tests for new functionality
   - Update documentation as needed

4. **Test Your Changes**
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

5. **Commit and Push**
   ```bash
   git add .
   git commit -m "feat: add amazing new feature"
   git push origin feature/your-feature-name
   ```

6. **Submit a Pull Request**
   - Use our PR template
   - Provide clear description of changes
   - Link related issues

## 🛠️ Development Setup

### Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **JDK**: 17 or higher
- **Android SDK**: API 24-35
- **Git**: Latest version
- **Firebase Account**: For backend services

### Environment Setup

1. **Clone and Setup**
   ```bash
   git clone https://github.com/yourusername/ksl-translator.git
   cd ksl-translator/android
   ```

2. **Configure Firebase**
   - Create Firebase project
   - Download `google-services.json`
   - Place in `app/` directory

3. **Set API Keys**
   ```properties
   # local.properties
   GEMINI_API_KEY=your_api_key_here
   ```

4. **Install Dependencies**
   ```bash
   ./gradlew build
   ```

### Development Tools

We recommend these tools for development:

- **IDE**: Android Studio with Kotlin plugin
- **Linting**: ktlint for code formatting
- **Testing**: JUnit 5 + Espresso
- **Debugging**: Android Studio debugger + Firebase console
- **Version Control**: Git with conventional commits

## 📝 Coding Standards

### Kotlin Style Guide

We follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

#### File Structure
```kotlin
// File header with license
/*
 * Copyright 2024 KSL Translator Contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.jerry.ksl.gesturerecognizer

// Imports (sorted)
import android.content.Context
import androidx.lifecycle.ViewModel
// ... other imports

/**
 * Class description with purpose and usage
 * @param context Application context
 * @param listener Callback for events
 */
class ExampleClass(
    private val context: Context,
    private val listener: EventListener
) : ViewModel() {
    
    companion object {
        private const val TAG = "ExampleClass"
        const val DEFAULT_TIMEOUT = 5000L
    }
    
    // Properties
    private val _state = MutableLiveData<State>()
    val state: LiveData<State> = _state
    
    // Methods
    fun doSomething() {
        // Implementation
    }
}
```

#### Naming Conventions
- **Classes**: PascalCase (`UserRepository`)
- **Functions**: camelCase (`getUserProfile`)
- **Properties**: camelCase (`isAuthenticated`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_RETRY_COUNT`)
- **Resources**: snake_case (`fragment_camera.xml`)

#### Documentation Requirements
All public APIs must have KDoc documentation:

```kotlin
/**
 * Authenticates user with Firebase and returns user profile
 * 
 * @param email User's email address
 * @param password User's password
 * @return Result containing User object or error
 * @throws AuthenticationException if credentials are invalid
 */
suspend fun authenticateUser(email: String, password: String): Result<User>
```

### Architecture Guidelines

#### MVVM Pattern
- **Views**: Activities and Fragments handle UI only
- **ViewModels**: Business logic and state management
- **Models**: Data classes and repositories
- **Services**: Specialized business logic (auth, validation, etc.)

#### Repository Pattern
```kotlin
interface UserRepository {
    suspend fun getUser(id: String): Result<User>
    suspend fun saveUser(user: User): Result<Unit>
}

class FirebaseUserRepository : UserRepository {
    override suspend fun getUser(id: String): Result<User> {
        // Implementation
    }
}
```

#### Error Handling
Use sealed classes for error states:

```kotlin
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val exception: Exception) : AuthResult()
    object Loading : AuthResult()
}
```

## 🧪 Testing Requirements

### Test Coverage Goals
- **Unit Tests**: 90%+ coverage for business logic
- **Integration Tests**: All Firebase operations
- **UI Tests**: Critical user flows
- **Performance Tests**: ML model accuracy and speed

### Testing Structure
```
src/test/                          # Unit tests
├── java/
│   ├── viewmodel/
│   │   ├── MainViewModelTest.kt
│   │   └── ValidationViewModelTest.kt
│   ├── service/
│   │   ├── AuthServiceTest.kt
│   │   └── ValidationServiceTest.kt
│   └── repository/
│       └── UserRepositoryTest.kt

src/androidTest/                   # Integration & UI tests
├── java/
│   ├── ui/
│   │   ├── CameraFragmentTest.kt
│   │   └── AuthFlowTest.kt
│   ├── integration/
│   │   ├── FirebaseIntegrationTest.kt
│   │   └── MediaPipeIntegrationTest.kt
│   └── performance/
│       └── RecognitionPerformanceTest.kt
```

### Test Examples

#### Unit Test
```kotlin
@Test
fun `authenticateUser should return success when credentials are valid`() = runTest {
    // Given
    val email = "test@example.com"
    val password = "password123"
    val expectedUser = User(id = "123", email = email)
    
    every { authService.signIn(email, password) } returns Result.success(expectedUser)
    
    // When
    val result = authRepository.authenticateUser(email, password)
    
    // Then
    assertThat(result.isSuccess).isTrue()
    assertThat(result.getOrNull()).isEqualTo(expectedUser)
}
```

#### UI Test
```kotlin
@Test
fun loginFlow_successfulAuthentication_navigatesToHome() {
    // Given
    onView(withId(R.id.etEmail)).perform(typeText("test@example.com"))
    onView(withId(R.id.etPassword)).perform(typeText("password123"))
    
    // When
    onView(withId(R.id.btnLogin)).perform(click())
    
    // Then
    onView(withId(R.id.navigation_home)).check(matches(isDisplayed()))
}
```

### Running Tests
```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# Test coverage report
./gradlew jacocoTestReport

# Performance tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=PerformanceTestSuite
```

## 🔄 Pull Request Process

### Before Submitting

1. **Update Documentation**: Ensure README and code docs reflect changes
2. **Add Tests**: Include tests for new functionality
3. **Run Tests**: Verify all tests pass locally
4. **Check Linting**: Run ktlint and fix any issues
5. **Update Changelog**: Add entry to CHANGELOG.md

### PR Template

When creating a PR, use this template:

```markdown
## Description
Brief description of changes and motivation.

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to change)
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Code refactoring

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed
- [ ] Performance impact assessed

## Screenshots (if applicable)
Add screenshots for UI changes.

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests added/updated
- [ ] No merge conflicts
```

### Review Process

1. **Automated Checks**: CI/CD pipeline runs tests and linting
2. **Code Review**: At least one maintainer reviews changes
3. **Testing**: Reviewer tests functionality manually
4. **Approval**: Changes approved by maintainer
5. **Merge**: PR merged into main branch

### Review Criteria

Reviewers will check for:
- **Functionality**: Does the code work as intended?
- **Code Quality**: Is the code clean and maintainable?
- **Tests**: Are there adequate tests?
- **Documentation**: Is documentation updated?
- **Performance**: Does it impact app performance?
- **Security**: Are there any security concerns?

## 🐛 Issue Reporting

### Bug Reports

Use the bug report template:

```markdown
## Bug Description
Clear description of the bug.

## Steps to Reproduce
1. Go to '...'
2. Click on '...'
3. Scroll down to '...'
4. See error

## Expected Behavior
What you expected to happen.

## Actual Behavior
What actually happened.

## Environment
- Device: [e.g. Pixel 6]
- OS Version: [e.g. Android 13]
- App Version: [e.g. 1.0.0]

## Screenshots
Add screenshots if applicable.

## Additional Context
Any other relevant information.
```

### Feature Requests

Use the feature request template:

```markdown
## Feature Description
Clear description of the feature.

## Problem Statement
What problem does this solve?

## Proposed Solution
How should this feature work?

## Alternative Solutions
Other approaches considered.

## Additional Context
Mockups, examples, or relevant information.
```

### Security Issues

For security vulnerabilities, please email [security@ksltranslator.com](mailto:security@ksltranslator.com) instead of creating a public issue.

## 📚 Documentation Guidelines

### README Updates
- Keep README current with new features
- Include setup instructions for new dependencies
- Update architecture diagrams when needed
- Add troubleshooting for common issues

### Code Documentation
- All public APIs need KDoc comments
- Include usage examples in documentation
- Document complex algorithms and business logic
- Add inline comments for non-obvious code

### API Documentation
- Document all REST endpoints
- Include request/response examples
- Document authentication requirements
- Provide error code explanations

### User Documentation
- Create user guides for new features
- Include screenshots and videos
- Write clear step-by-step instructions
- Translate documentation when possible

## 🌟 Community Guidelines

### Communication
- **Be Respectful**: Treat all community members with respect
- **Be Inclusive**: Welcome people of all backgrounds and experience levels
- **Be Constructive**: Provide helpful feedback and suggestions
- **Be Patient**: Remember that everyone is learning

### Getting Help
- **GitHub Discussions**: For general questions and discussions
- **GitHub Issues**: For bug reports and feature requests
- **Discord**: For real-time chat and support
- **Email**: For private or sensitive matters

### Recognition
We recognize contributors in several ways:
- **Contributors File**: Listed in CONTRIBUTORS.md
- **Release Notes**: Mentioned in release announcements
- **Social Media**: Highlighted on project social accounts
- **Badges**: GitHub profile badges for contributions

### Mentorship
We offer mentorship for new contributors:
- **Good First Issues**: Labeled for newcomers
- **Mentorship Program**: Pairing with experienced contributors
- **Documentation**: Comprehensive guides for getting started
- **Code Reviews**: Educational feedback during reviews

## 📞 Contact

- **General Questions**: [community@ksltranslator.com](mailto:community@ksltranslator.com)
- **Technical Issues**: [support@ksltranslator.com](mailto:support@ksltranslator.com)
- **Security Concerns**: [security@ksltranslator.com](mailto:security@ksltranslator.com)
- **Code of Conduct**: [conduct@ksltranslator.com](mailto:conduct@ksltranslator.com)

## 🙏 Thank You

Thank you for contributing to KSL Translator! Your efforts help make sign language technology more accessible to everyone.

---

*This contributing guide is adapted from best practices in the open source community and is regularly updated based on contributor feedback.*
