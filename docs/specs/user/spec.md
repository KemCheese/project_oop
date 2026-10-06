# Spec: user

> Auto-extracted by spec-miner. Last mined: 2026-10-06.
> Source: RegisterUserUseCase.java, UserDetailsServiceImpl.java, UserProfileService.java, UserQueryService.java, UserController.java, StarredTicketUseCase.java
> Last verified: 2026-10-06 (commit d985aff)

---

### Requirement: Register Account with Baseline Reputation
<!-- id: RegisterUserUseCase.register -->
<!-- entities: User, UserReputationScore -->
<!-- enforced: RegisterUserUseCase.register() -->

The system SHALL register new user accounts by validating password confirmation, ensuring email uniqueness, encoding passwords with BCrypt, setting role to USER, and synchronously provisioning an initial UserReputationScore of 100.00 points.

#### Scenario: Successful User Registration
<!-- test: RegisterUserUseCaseTest.testRegisterSuccess() -->
- **WHEN** a guest submits valid registration details (name, email, password matching confirmPassword)
- **THEN** a User record is created with BCrypt passwordHash, status ACTIVE (or PENDING_VERIFICATION), and an associated reputation score of 100.00 is initialized

#### Scenario: Duplicate Email Registration Rejection
<!-- test: RegisterUserUseCaseTest.testDuplicateEmailThrows() -->
- **WHEN** a guest attempts registration with an email address already present in UserRepository
- **THEN** the system rejects registration with BusinessRuleViolationException("Email đã được đăng ký")

---

### Requirement: Authenticate and Map User Status to Security Flags
<!-- id: UserDetailsServiceImpl.loadUserByUsername -->
<!-- entities: User, UserStatus, UserRole -->
<!-- enforced: UserDetailsServiceImpl.loadUserByUsername() -->

The system SHALL load user credentials for Spring Security and map UserStatus to account flags: ACTIVE enables login, PENDING_VERIFICATION and SUSPENDED set enabled=false (DisabledException), and BANNED sets accountNonLocked=false (LockedException).

#### Scenario: Active User Login Authentication
<!-- test: UserDetailsServiceImplTest.testLoadActiveUser() -->
- **WHEN** an active user credentials are submitted
- **THEN** UserDetails is returned with enabled=true, accountNonLocked=true, and authority "ROLE_USER"

#### Scenario: Banned User Login Attempt
<!-- test: UserDetailsServiceImplTest.testLoadBannedUserThrowsLocked() -->
- **WHEN** a user with status BANNED attempts authentication
- **THEN** UserDetails returns accountNonLocked=false and Spring Security rejects authentication with LockedException

---

### Requirement: Update Profile and Reset Phone Verification
<!-- id: UserProfileService.updateProfile -->
<!-- entities: User -->
<!-- enforced: UserProfileService.updateProfile() -->

The system SHALL update fullName and phoneNumber in the user profile, automatically resetting the phoneVerified flag to false whenever the phone number is altered.

#### Scenario: Phone Number Change Resets Verification Flag
<!-- test: UserProfileServiceTest.testUpdatePhoneNumberResetsFlag() -->
- **WHEN** a user updates their profile with a new phone number
- **THEN** the new phone number is saved and phoneVerified is reset to false

#### Scenario: Name-Only Update Preserves Verification Flag
<!-- test: UserProfileServiceTest.testUpdateNameKeepsPhoneFlag() -->
- **WHEN** a user updates fullName while keeping the existing phone number unchanged
- **THEN** the existing phoneVerified value remains untouched

---

### Requirement: Upload Avatar with Format Validation and Clean Deletion
<!-- id: UserProfileService.updateAvatar -->
<!-- entities: User, StoredFileRef -->
<!-- enforced: UserProfileService.updateAvatar() -->

The system SHALL validate avatar uploads against permitted MIME types (image/jpeg, image/png, image/webp) and maximum 10MB size, save the new avatar via FileStorageService, and delete the prior avatar file fail-safely.

#### Scenario: Valid Avatar Image Upload
<!-- test: UserProfileServiceTest.testUploadValidAvatar() -->
- **WHEN** a user uploads a valid PNG image under 10MB
- **THEN** the image is stored in folder "avatars", user avatarStorageKey is updated, and previous avatar file is deleted

#### Scenario: Invalid MIME Type Rejection
<!-- test: UserProfileServiceTest.testUploadInvalidMimeThrows() -->
- **WHEN** a user attempts to upload a PDF or executable file as an avatar
- **THEN** the system rejects the file with BusinessRuleViolationException

---

### Requirement: Manage Bank Account Details with Data Masking
<!-- id: UserProfileService.updateBankAccount -->
<!-- entities: User -->
<!-- enforced: UserProfileService.updateBankAccount() -->

The system SHALL allow users to update their banking details (bank name, account number, account holder) and enforce data masking on display such that only the last 4 digits are revealed.

#### Scenario: User Updates Bank Account
<!-- test: UserProfileServiceTest.testUpdateBankSuccess() -->
- **WHEN** a user submits valid bank credentials
- **THEN** details are securely persisted to the User entity with optimistic locking version check

#### Scenario: Profile View Displays Masked Bank Account
<!-- test: UserControllerTest.testShowProfileMasksBankAccount() -->
- **WHEN** a user navigates to their profile page
- **THEN** the bank account number is rendered in masked format (e.g. "**** **** **** 1234")

---

### Invariant: BCrypt Password Hashing Mandatory
<!-- entities: User -->
<!-- enforced: RegisterUserUseCase.register() -->
<!-- verified_by: RegisterUserUseCaseTest.testPasswordNeverStoredPlaintext() -->

The passwordHash field of any persisted User entity SHALL ALWAYS begin with the BCrypt prefix `$2a$` or `$2b$`, and cleartext passwords SHALL NEVER touch the database.

> Last verified: 2026-10-06 (commit d985aff)

---

### Invariant: Optimistic Concurrency on User Profile
<!-- entities: User -->
<!-- enforced: User.java -->

The User entity SHALL maintain a JPA @Version field to prevent lost updates when concurrent profile updates occur.

> Last verified: 2026-10-06 (commit d985aff)
