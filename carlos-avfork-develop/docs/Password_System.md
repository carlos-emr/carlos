# CARLOS EMR Password System

## Overview

CARLOS EMR uses a modern password hashing system based on Spring Security's `DelegatingPasswordEncoder` with BCrypt as the primary hashing algorithm. This system supports both modern BCrypt hashes and legacy SHA hashes for backward compatibility.

## Password Hashing Architecture

### Current Implementation (Recommended)

CARLOS uses **BCrypt** for all new passwords with the following characteristics:

- **Algorithm**: BCrypt with cost factor 12
- **Format**: `{bcrypt}$2b$12$...` (Spring Security DelegatingPasswordEncoder format)
- **Storage**: Full hash including `{bcrypt}` prefix stored in `security.password` field
- **Security**: Industry-standard, salt-based, computationally expensive

### Legacy Support

CARLOS maintains backward compatibility with legacy SHA hashes:

- **Format**: Concatenated byte values (e.g., `-51-282443-97-5-9410489-60-1021-45-127-12435464-32`)
- **Usage**: Existing passwords only, automatically upgraded when user logs in
- **Status**: Deprecated, will be upgraded to BCrypt on next login
- **Write policy**: New SHA/SHA-1 password hashes must not be created; all new password writes use
  `PasswordHashHelper`/`EncryptionUtils.hash()` and produce `{bcrypt}` hashes.
- **Static-analysis handling**: The remaining SHA-1 digest call is intentionally limited to the legacy
  password verifier and carries FindSecBugs/Semgrep/Sonar suppressions with justification.

## Database Schema

### Security Table

```sql
CREATE TABLE security (
    security_no int PRIMARY KEY,
    user_name varchar(30) NOT NULL,
    password varchar(255) NOT NULL,  -- BCrypt hash with {bcrypt} prefix
    provider_no varchar(6),
    pin varchar(10),
    forcePasswordReset tinyint(1) DEFAULT 0,
    -- ... other fields
);
```

### Example Records

**Modern BCrypt Password:**
```sql
INSERT INTO security(security_no, user_name, password, provider_no, pin, forcePasswordReset) 
VALUES (128, 'carlosdoc', '{bcrypt}$2a$12$AiWd9O1jX9Lz//qvLIvNzuAFrmVEtvuVovnX.APkdH5420AX/NDNO', '999998', '2026', 1);
```

**Legacy SHA Password (deprecated):**
```sql
-- This format is automatically upgraded on login
INSERT INTO security(security_no, user_name, password, provider_no, pin, forcePasswordReset) 
VALUES (129, 'legacy_user', '-51-282443-97-5-9410489-60-1021-45-127-12435464-32', '999999', '1234', 1);
```

## Code Implementation

### Key Classes

1. **`PasswordHashHelper`** (`io.github.carlos_emr.carlos.utility.password.PasswordHashHelper`)
   - Primary interface for password operations
   - Methods: `encodePassword()`, `matches()`, `upgradeEncoding()`

2. **`EncryptionUtils`** (`io.github.carlos_emr.carlos.utility.EncryptionUtils`)
   - Wrapper around PasswordHashHelper
   - Methods: `hash()`, `verify()`, `isPasswordHashUpgradeNeeded()`

3. **`SecurityManager`** (`io.github.carlos_emr.carlos.managers.SecurityManager`)
   - Business logic for authentication
   - Methods: `validatePassword()`, `matchesPassword()`, `upgradeSavePasswordHash()`

### Password Validation Flow

```java
// Illustrative authentication process. See SecurityManager.java for the live implementation.
public boolean validatePassword(CharSequence rawPassword, Security security) {
    boolean isValid = this.matchesPassword(rawPassword, security.getPassword());
    
    // Auto-upgrade legacy hashes
    if (isValid && EncryptionUtils.isPasswordHashUpgradeNeeded(security.getPassword())) {
        boolean isHashUpgraded = this.upgradeSavePasswordHash(rawPassword, security);
        if (!isHashUpgraded) {
            logger.error("Error while upgrading password hash");
        }
    }
    return isValid;
}
```

### Password Creation

```java
// Generate new password hash
String newPasswordHash = EncryptionUtils.hash("user_password");
// Result: {bcrypt}$2b$12$...

// Verify password
boolean isValid = EncryptionUtils.verify("user_password", storedHash);
```

## Password Management

### Creating New Users

1. **Generate BCrypt hash**:
   ```bash
   python3 scripts/generate_bcrypt_password.py "new_password"
   ```

2. **Insert into database**:
   ```sql
   INSERT INTO security(security_no, user_name, password, provider_no, pin, forcePasswordReset)
   VALUES (130, 'new_user', '{bcrypt}$2b$12$generated_hash_here', '999997', '1234', 1);
   ```

### Password Reset Process

1. **Admin generates new hash**:
   ```bash
   python3 scripts/generate_bcrypt_password.py "temporary_password"
   ```

2. **Update database**:
   ```sql
   UPDATE security 
   SET password = '{bcrypt}$2b$12$new_hash_here', forcePasswordReset = 1 
   WHERE user_name = 'username';
   ```

3. **User must change password on next login**

### Bulk Password Updates

For multiple users or system migrations:

```bash
# Generate hashes for multiple users
for user in user1 user2 user3; do
    echo "Password for $user:"
    python3 scripts/generate_bcrypt_password.py "default_password_$user"
    echo "---"
done
```

## Security Considerations

### Best Practices

1. **Use Strong Passwords**:
   - Minimum 8 characters
   - Mix of uppercase, lowercase, numbers, symbols
   - No dictionary words or personal information

2. **Force Password Reset**:
   - Set `forcePasswordReset = 1` for new accounts
   - Set for temporary/default passwords
   - Forced reset is a multi-step login flow: the first successful credential check stages an
     opaque server-side credential-cache token, and the password update POST consumes that token
     only after old-password, confirmation, complexity, and CSRF validation pass.
   - Browser JavaScript provides immediate feedback, but direct POSTs are still validated
     server-side against `password_min_length`, `password_min_groups`, group character sets, and
     `IGNORE_PASSWORD_REQUIREMENTS`.

3. **PIN Requirements**:
   - Separate PIN for additional security layer
   - Minimum 4 digits recommended

### Configuration

Password policies can be configured in `carlos.properties`:

```properties
# Password-history policy uses the legacy password.* namespace.
# Previous passwords to check (prevent reuse); shipped default is 0.
password.pastPasswordsToNotUse=0

# Forced-reset routing and complexity use legacy underscore-separated keys.
# Server-side forced password reset gate (enabled by default).
# Accepted true values are true/yes/on/1; unrecognized values fail secure as enabled.
mandatory_password_reset=true

# Forced-reset complexity policy
password_min_length = 8
password_min_groups = 3
password_group_lower_chars = abcdefghijklmnopqrstuvwxyz
password_group_upper_chars = ABCDEFGHIJKLMNOPQRSTUVWXYZ
password_group_digits = 0123456789
password_group_special = \! @\#$%^&*()_+|~-\=\\`{}[]\:";'<>?,./
```

## Default Credentials

### Development/Testing

**Default User**: `carlosdoc`
- **Password**: `carlos2026`
- **PIN**: `2026`
- **Hash**: `{bcrypt}$2a$12$AiWd9O1jX9Lz//qvLIvNzuAFrmVEtvuVovnX.APkdH5420AX/NDNO`

⚠️ **Security Warning**: Change default credentials immediately in production environments!

### Production Setup

1. **Change default user**:
   ```sql
   UPDATE security SET password = 'new_bcrypt_hash' WHERE user_name = 'carlosdoc';
   ```

2. **Or create new admin user**:
   ```sql
   INSERT INTO provider VALUES ('999997','admin','Administrator','Admin',...);
   INSERT INTO security(security_no, user_name, password, provider_no, pin, forcePasswordReset)
   VALUES (131, 'admin', '{bcrypt}$generated_hash', '999997', '9999', 1);
   ```

3. **Disable/expire default account**:
   ```sql
   UPDATE security SET date_ExpireDate = CURDATE(), b_ExpireSet = 1 
   WHERE user_name = 'carlosdoc';
   ```

## Migration Guide

### From Legacy SHA to BCrypt

Legacy passwords are automatically upgraded during login, but manual migration is possible:

1. **Identify legacy passwords**:
   ```sql
   SELECT user_name, password FROM security 
   WHERE password NOT LIKE '{bcrypt}%' AND password NOT LIKE '{%';
   ```

2. **Manual upgrade** (requires knowing plain text password):
   ```python
   # Use generate_bcrypt_password.py script
   python3 scripts/generate_bcrypt_password.py "known_password"
   ```

3. **Update database**:
   ```sql
   UPDATE security SET password = '{bcrypt}$2b$12$new_hash' 
   WHERE user_name = 'username';
   ```

### Troubleshooting

**Login Issues**:
1. Verify hash format includes `{bcrypt}` prefix
2. Check database encoding (UTF-8 recommended)
3. Verify PIN if PIN checking is enabled
4. Check account expiration settings

**Hash Generation Issues**:
1. Ensure bcrypt library is installed: `pip3 install bcrypt`
2. Use Python 3.x (not Python 2.x)
3. Verify script permissions: `chmod +x generate_bcrypt_password.py`

## Tools and Scripts

### Password Generation Script

Location: `scripts/generate_bcrypt_password.py`

```bash
# Generate hash for specific password
python3 scripts/generate_bcrypt_password.py "my_password"

# Interactive mode (password hidden)
python3 scripts/generate_bcrypt_password.py

# Example output:
# Password: my_password
# BCrypt hash: {bcrypt}$2b$12$...
# Database SQL: UPDATE sec SET password='{bcrypt}...' WHERE user_name='your_username';
```

### Database Queries

**Check password format**:
```sql
SELECT user_name, 
       CASE 
           WHEN password LIKE '{bcrypt}%' THEN 'BCrypt (Modern)'
           WHEN password LIKE '{%' THEN 'Other Encoded'
           ELSE 'Legacy SHA'
       END as password_type,
       LENGTH(password) as password_length
FROM security;
```

**Find users needing upgrade**:
```sql
SELECT user_name FROM security 
WHERE password NOT LIKE '{%' 
ORDER BY user_name;
```

## References

- Spring Security DelegatingPasswordEncoder: [Documentation](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage-dpe)
- BCrypt Algorithm: [Wikipedia](https://en.wikipedia.org/wiki/Bcrypt)
- CARLOS Security Classes: `io.github.carlos_emr.carlos.utility.password.*`, `io.github.carlos_emr.carlos.managers.SecurityManager`
