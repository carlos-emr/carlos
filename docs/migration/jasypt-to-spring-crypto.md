# Migration: jasypt to Spring Security Crypto

**Date**: 2026-01-28
**Issue**: [#2158](https://github.com/openo-beta/Open-O/issues/2158)
**Migration**: Replace jasypt 1.9.3 with Spring Security Crypto 6.3.9

## Overview

OpenO EMR has migrated from the stale jasypt library (last updated 2017) to Spring Security Crypto for encryption functionality. This migration affects the Shared Outcomes Dashboard integration.

## What Changed

### Code Changes

**File Modified**: `src/main/java/ca/openosp/openo/managers/DashboardManagerImpl.java`

**Before** (jasypt):
```java
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Base64;

Security.addProvider(new BouncyCastleProvider());
StandardPBEStringEncryptor encrypter = new StandardPBEStringEncryptor();
encrypter.setAlgorithm("PBEWITHSHA256AND256BITAES-CBC-BC");
encrypter.setProviderName("BC");
encrypter.setPassword(OscarProperties.getInstance().getProperty("shared_outcomes_dashboard_key"));
encrypted = encrypter.encrypt(json);
b64 = Base64.toBase64String(encrypted.getBytes());
```

**After** (Spring Security Crypto):
```java
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

String password = OscarProperties.getInstance().getProperty("shared_outcomes_dashboard_key");
String salt = OscarProperties.getInstance().getProperty("shared_outcomes_dashboard_salt");

BytesEncryptor encryptor = Encryptors.stronger(password, salt);
byte[] encrypted = encryptor.encrypt(json.getBytes(StandardCharsets.UTF_8));
b64 = Base64.getEncoder().encodeToString(encrypted);
```

### Dependency Changes

**Removed from pom.xml**:
```xml
<dependency>
    <groupId>org.jasypt</groupId>
    <artifactId>jasypt</artifactId>
    <version>1.9.3</version>
</dependency>
```

**Already Available** (no changes needed):
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>6.3.9</version>
</dependency>
```

## Configuration Changes Required

### New Property Required: `shared_outcomes_dashboard_salt`

**IMPORTANT**: This migration adds a **required** new property to `oscar.properties`.

#### Generate Salt Value

Use OpenSSL to generate a random 16-byte hex salt:

```bash
openssl rand -hex 16
```

Example output: `8f3c21ab56789def1234567890abcdef`

#### Add to oscar.properties

```properties
# Shared Outcomes Dashboard Encryption
# Password for encrypting dashboard URL parameters
shared_outcomes_dashboard_key=<your-secure-password>

# Salt for encryption (REQUIRED as of version X.X.X)
# Generate with: openssl rand -hex 16
shared_outcomes_dashboard_salt=8f3c21ab56789def1234567890abcdef
```

### Validation

The code now validates that both properties are present:

```java
if (password == null || password.isEmpty()) {
    throw new IllegalArgumentException("shared_outcomes_dashboard_key property is required");
}
if (salt == null || salt.isEmpty()) {
    throw new IllegalArgumentException("shared_outcomes_dashboard_salt property is required");
}
```

If either property is missing, the application will log an error, and the generated dashboard URL will contain `encodedParams=null` (for example, `https://dashboard.example.com?encodedParams=null&version=1.1`).

## Breaking Changes

### External Dashboard Integration

⚠️ **CRITICAL**: If you use the Shared Outcomes Dashboard integration:

1. **Encryption Algorithm Changed**:
   - Old: `PBEWITHSHA256AND256BITAES-CBC-BC` (Bouncy Castle)
   - New: AES-256-CBC with PKCS5 padding (Spring Security Crypto)

2. **Output Format**:
   - Old: Base64-encoded string from Bouncy Castle encoder
   - New: Base64-encoded string from Java standard encoder

3. **Encryption Behavior**:
   - Both use random IV generation (different output each time)
   - Both use 256-bit AES encryption
   - Both are secure for PHI protection

4. **External Dashboard Decryption**:
   - External dashboard must be updated to decrypt using Spring Security Crypto
   - Or implement compatible decryption algorithm
   - Coordinate deployment with external dashboard team

### Decryption Example (Java)

If the external dashboard is Java-based, use this decryption code:

```java
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

String password = "shared-password";  // Same as shared_outcomes_dashboard_key
String salt = "8f3c21ab56789def1234567890abcdef";  // Same as shared_outcomes_dashboard_salt

BytesEncryptor encryptor = Encryptors.stronger(password, salt);

// Decrypt from URL parameter
String encodedParams = request.getParameter("encodedParams");
byte[] decoded = Base64.getDecoder().decode(encodedParams);
byte[] decrypted = encryptor.decrypt(decoded);
String json = new String(decrypted, StandardCharsets.UTF_8);
```

## Upgrade Steps

### For Existing Deployments

1. **Generate salt value**:
   ```bash
   openssl rand -hex 16
   ```

2. **Update oscar.properties**:
   ```properties
   shared_outcomes_dashboard_salt=<generated-salt-value>
   ```

3. **Deploy updated application**:
   - Stop Tomcat
   - Deploy new WAR
   - Start Tomcat
   - Verify logs for encryption errors

4. **Coordinate with external dashboard team** (if applicable):
   - Share new encryption algorithm details
   - Share password and salt values (securely)
   - Coordinate deployment timeline
   - Test integration in staging first

5. **Monitor logs** for 48 hours after deployment

### For New Deployments

Include both properties in initial `oscar.properties` configuration:

```properties
shared_outcomes_dashboard_url=https://dashboard.example.com
shared_outcomes_dashboard_key=<secure-password>
shared_outcomes_dashboard_salt=<generated-salt>
shared_outcomes_dashboard_clinic_id=<clinic-identifier>
```

## Testing

### Automated Tests

Run the new test suite:

```bash
mvn test -Dtest=DashboardManagerEncryptionIntegrationTest
```

Tests verify:
- ✅ Valid URL generation with encryption
- ✅ Base64 output format
- ✅ Random IV generation (different output each time)
- ✅ Round-trip encryption/decryption
- ✅ Error handling for missing configuration

### Manual Testing

1. **Verify configuration**:
   ```bash
   grep shared_outcomes_dashboard oscar.properties
   ```

2. **Generate shared dashboard URL**:
   - Log in to OpenO EMR
   - Navigate to Shared Outcomes Dashboard
   - Verify URL is generated without errors

3. **Test external dashboard** (if applicable):
   - Copy generated URL
   - Open in browser
   - Verify dashboard loads with correct user/clinic data

## Rollback Plan

If the migration causes issues:

1. **Revert code changes**:
   ```bash
   git revert <commit-hash>
   ```

2. **Redeploy previous version**:
   - Stop Tomcat
   - Deploy previous WAR
   - Start Tomcat

3. **Remove salt property** (optional):
   ```bash
   # Comment out or remove from oscar.properties
   # shared_outcomes_dashboard_salt=...
   ```

4. **Timeline**: < 30 minutes rollback

## Benefits

- ✅ **Modern encryption library**: Spring Security Crypto actively maintained
- ✅ **Reduced dependencies**: Removed 8-year-old jasypt library
- ✅ **Security**: Uses industry-standard AES-256-CBC encryption
- ✅ **PHI protection**: Maintains HIPAA/PIPEDA compliance
- ✅ **Integration**: Better integration with Spring ecosystem

## Support

For questions or issues with this migration:

1. **Documentation**: This file (`docs/migration/jasypt-to-spring-crypto.md`)
2. **Issue Tracker**: [GitHub Issue #2158](https://github.com/openo-beta/Open-O/issues/2158)
3. **Code Reference**: `DashboardManagerImpl.java:748-766`
4. **Test Reference**: `DashboardManagerEncryptionIntegrationTest.java`

## References

- **Spring Security Crypto Documentation**: https://docs.spring.io/spring-security/reference/features/integrations/cryptography.html
- **jasypt (deprecated)**: https://github.com/jasypt/jasypt
- **Issue #2158**: Replace jasypt 1.9.3 with Spring Security Crypto
- **Parent Issue #2139**: Dependency Audit and Modernization
