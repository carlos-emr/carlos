# OpenO EMR Password System

## Overview

OpenO EMR uses a modern password hashing system based on Spring Security's `DelegatingPasswordEncoder` with BCrypt as the primary hashing algorithm. This system supports both modern BCrypt hashes and legacy SHA hashes for backward compatibility.

## Password Hashing Architecture

### Current Implementation (Recommended)

OpenO uses **BCrypt** for all new passwords with the following characteristics:

- **Algorithm**: BCrypt with cost factor 12
- **Format**: `{bcrypt}$2b$12$...` (Spring Security DelegatingPasswordEncoder format)
- **Storage**: Full hash including `{bcrypt}` prefix stored in `security.password` field
- **Security**: Industry-standard, salt-based, computationally expensive

### Legacy Support

OpenO maintains backward compatibility with legacy SHA hashes:

- **Format**: Concatenated byte values (e.g., `-51-282443-97-5-9410489-60-1021-45-127-12435464-32`)
- **Usage**: Existing passwords only, automatically upgraded when user logs in
- **Status**: Deprecated, will be upgraded to BCrypt on next login

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
VALUES (128, 'openodoc', '{bcrypt}$2b$12$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96', '999998', '2025', 1);
```

**Legacy SHA Password (deprecated):**
```sql
-- This format is automatically upgraded on login
INSERT INTO security(security_no, user_name, password, provider_no, pin, forcePasswordReset) 
VALUES (129, 'legacy_user', '-51-282443-97-5-9410489-60-1021-45-127-12435464-32', '999999', '1234', 1);
```

## Code Implementation

### Key Classes

1. **`PasswordHashHelper`** (`ca.openosp.openo.utility.password.PasswordHashHelper`)
   - Primary interface for password operations
   - Methods: `encodePassword()`, `matches()`, `upgradeEncoding()`

2. **`EncryptionUtils`** (`ca.openosp.openo.utility.EncryptionUtils`)
   - Wrapper around PasswordHashHelper
   - Methods: `hash()`, `verify()`, `isPasswordHashUpgradeNeeded()`

3. **`SecurityManager`** (`ca.openosp.openo.managers.SecurityManager`)
   - Business logic for authentication
   - Methods: `validatePassword()`, `matchesPassword()`, `upgradeSavePasswordHash()`

### Password Validation Flow

```java
// Authentication process
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

3. **PIN Requirements**:
   - Separate PIN for additional security layer
   - Minimum 4 digits recommended

### Configuration

Password policies can be configured in `oscar.properties`:

```properties
# Previous passwords to check (prevent reuse)
password.pastPasswordsToNotUse=5

# Password expiration (if enabled)
password.forcePasswordReset=true
password.expirationDays=90
```

## Default Credentials

### Development/Testing

**Default User**: `openodoc`
- **Password**: `openo2025`
- **PIN**: `2025`
- **Hash**: `{bcrypt}$2b$12$9mdpjGHFmuVrW7uv7HlZter.6Gdqx.V/i.ba52e9VP6ZYnwJR6h96`

⚠️ **Security Warning**: Change default credentials immediately in production environments!

### Production Setup

1. **Change default user**:
   ```sql
   UPDATE security SET password = 'new_bcrypt_hash' WHERE user_name = 'openodoc';
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
   WHERE user_name = 'openodoc';
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
- OpenO Security Classes: `ca.openosp.openo.utility.password.*`, `ca.openosp.openo.managers.SecurityManager`