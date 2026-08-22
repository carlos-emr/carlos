# PathValidationUtils - Secure File Path Validation

## Overview

`PathValidationUtils` is a centralized utility class for validating file paths to prevent path traversal attacks in CARLOS EMR. It provides consistent, robust validation across the entire codebase.

**Package**: `io.github.carlos_emr.carlos.utility.PathValidationUtils`
**Since**: 2025-12-09

## Why Use PathValidationUtils?

Before PathValidationUtils, path validation was implemented inconsistently across the codebase with various patterns:
- Manual `canonicalPath.startsWith(baseDir + File.separator)` checks
- Custom sanitization methods in individual files
- Inconsistent temp directory validation

PathValidationUtils provides:
- **Consistency**: Single source of truth for path validation
- **Security**: Comprehensive validation including hidden file rejection, filename sanitization
- **Maintainability**: Easier to fix issues across all uses
- **Robustness**: Handles edge cases like symlinks, temp directories, Tomcat work directories

## API Reference

### validatePath(String userProvidedFileName, File allowedDir)

**Use for**: Validating user-provided filenames before creating/reading files in a directory.

**What it does**:
1. Sanitizes the filename (strips path components, rejects hidden files)
2. Validates the resulting path is within the allowed directory

```java
// Example: Secure file download
File directory = new File(documentDir);
File safeFile = PathValidationUtils.validatePath(userProvidedFilename, directory);
// Now safe to read from safeFile
```

**Returns**: `File` - The validated file path

**Throws**: `SecurityException` if validation fails

### validatePathComponent(String value, String label)

**Use for**: Validating a single path component that must be preserved exactly, such as a queue
directory name, document id segment, or existing server-side filename.

**What it does**:
1. Rejects null, blank, hidden, current-directory, and parent-directory values
2. Rejects Unix and Windows path separators, drive prefixes, UNC paths, and null bytes
3. Returns the original value unchanged after proving it is a safe single component

Use this when `validatePath()` would be too permissive because it strips path components. For
example, `validatePath("../../safe.pdf", dir)` resolves to `dir/safe.pdf`, but
`validatePathComponent("../../safe.pdf", "pdfName")` rejects the value.

```java
// Example: Preserve request components exactly, then validate the final path boundary
String queueId = PathValidationUtils.validatePathComponent(rawQueueId, "queueId");
String fileName = PathValidationUtils.validatePathComponent(rawFileName, "fileName");
File target = PathValidationUtils.validateExistingPath(
    new File(new File(incomingDocumentDir, queueId), fileName),
    incomingDocumentDir
);
// Now safe to read from target
```

**Returns**: `String` - The original component if valid

**Throws**: `SecurityException` if the value is not a safe single path component

### validateExistingPath(File file, File allowedDir)

**Use for**: Validating internal/application-created paths before access or deletion.

**What it does**:
1. Validates the file's canonical path is within the allowed directory
2. Does NOT sanitize or reconstruct the path

```java
// Example: Validate before file deletion
File fileToDelete = new File(filePath);
File docDir = new File(documentDir);
PathValidationUtils.validateExistingPath(fileToDelete, docDir);
// Now safe to delete
```

**Returns**: `File` - The same file if valid

**Throws**: `SecurityException` if the file is outside the allowed directory

### validateUpload(File sourceFile)

**Use for**: Validating uploaded source files from Struts2/Tomcat.

**What it does**:
1. Verifies source file exists and is a regular file
2. Validates the source is from an allowed temp location

```java
// Example: Validate upload before processing
PathValidationUtils.validateUpload(uploadedFile);
InputStream is = new FileInputStream(uploadedFile);
// Now safe to read content
```

**Throws**: `SecurityException` if validation fails

### validateUpload(File sourceFile, String userProvidedFileName, File destinationDir)

**Use for**: Complete end-to-end upload validation.

**What it does**:
1. Validates source file exists and is from an allowed location
2. Sanitizes the user-provided filename
3. Validates the destination path is within the allowed directory

```java
// Example: Complete upload workflow
File destination = PathValidationUtils.validateUpload(
    uploadedFile,
    originalFilename,
    new File(documentDir)
);
FileUtils.copyFile(uploadedFile, destination);
```

**Returns**: `File` - The validated destination path

**Throws**: `SecurityException` if any validation fails

### isInAllowedTempDirectory(File file)

**Use for**: Checking if a file is in an allowed system temp directory.

**What it does**:
- Checks if file is within:
  - `java.io.tmpdir` (System temp directory)
  - `catalina.base/work` (Tomcat work directory)
  - `catalina.home/work` (Tomcat home work directory)

```java
// Example: Validate temp file location
if (!PathValidationUtils.isInAllowedTempDirectory(tempFile)) {
    throw new SecurityException("Invalid temp file location");
}
```

**Returns**: `boolean` - true if in allowed temp directory

## Migration Guide

### Before (Old Pattern)
```java
// Manual validation - inconsistent and error-prone
String canonicalPath = file.getCanonicalPath();
String baseCanonical = baseDir.getCanonicalPath();
if (!canonicalPath.startsWith(baseCanonical + File.separator)) {
    throw new SecurityException("Path traversal detected");
}
```

### After (Using PathValidationUtils)
```java
// Clean, consistent, and robust
PathValidationUtils.validateExistingPath(file, baseDir);
```

### Common Migration Patterns

#### 1. Download/Read Operations
```java
// Old
String sanitizedFilename = filename.replaceAll("[\\/]", "");
File file = new File(dir, sanitizedFilename);
if (!file.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
    throw new SecurityException("Invalid path");
}

// New
File file = PathValidationUtils.validatePath(filename, new File(dir));
```

#### 2. Upload Validation
```java
// Old
String tempDir = System.getProperty("java.io.tmpdir");
String canonicalPath = uploadedFile.getCanonicalPath();
if (!canonicalPath.startsWith(new File(tempDir).getCanonicalPath() + File.separator)) {
    throw new SecurityException("Invalid upload source");
}

// New
PathValidationUtils.validateUpload(uploadedFile);
```

#### 3. File Deletion
```java
// Old
String canonicalPath = fileToDelete.getCanonicalPath();
if (!canonicalPath.startsWith(allowedDir.getCanonicalPath() + File.separator)) {
    throw new SecurityException("Cannot delete file outside directory");
}

// New
PathValidationUtils.validateExistingPath(fileToDelete, allowedDir);
```

#### 4. Multi-Directory Validation
```java
// Old
boolean isAllowed = canonicalPath.startsWith(docDir + File.separator) ||
                    canonicalPath.startsWith(tempDir + File.separator);
if (!isAllowed) throw new SecurityException("Invalid path");

// New
try {
    PathValidationUtils.validateExistingPath(file, new File(docDir));
} catch (SecurityException e) {
    if (!PathValidationUtils.isInAllowedTempDirectory(file)) {
        throw new SecurityException("Invalid path");
    }
}
```

#### 5. Request Components Used in Existing Paths
```java
// Old: validatePath() sanitizes a copy, but the original values are still used later
PathValidationUtils.validatePath(requestQueueId, incomingDir);
File target = new File(new File(incomingDir, requestQueueId), requestFileName);

// New: validate each component exactly, then validate the assembled path
String queueId = PathValidationUtils.validatePathComponent(requestQueueId, "queueId");
String fileName = PathValidationUtils.validatePathComponent(requestFileName, "fileName");
File target = PathValidationUtils.validateExistingPath(
    new File(new File(incomingDir, queueId), fileName),
    incomingDir
);
```

## Security Features

### Filename Sanitization
- Strips path components (directory prefixes)
- Rejects hidden files (starting with `.`)
- Uses Apache Commons IO FilenameUtils for cross-platform support

### Strict Path Component Validation
- Preserves the original value instead of normalizing or stripping it
- Rejects path separators, traversal components, drive prefixes, UNC prefixes, and null bytes
- Prevents the common anti-pattern of validating a sanitized copy and then using the original input

### Path Traversal Prevention
- Uses canonical path resolution to follow symlinks
- Validates containment with proper separator handling
- Rejects attempts to escape directories

### Temp Directory Handling
- Recognizes system temp directory
- Recognizes Tomcat work directories (where Struts2 stores uploads)
- Thread-safe lazy initialization of allowed directories

## Best Practices

1. **Always use PathValidationUtils** for any file operations involving user input
2. **Use `validatePath()`** when basename stripping is acceptable and you are creating a safe path inside an allowed directory
3. **Use `validatePathComponent()`** when a request-controlled directory or filename segment must be preserved exactly
4. **Use `validateExistingPath()`** for assembled or internal paths before access or deletion
5. **Use `validateUpload()`** for file uploads
6. **Use the returned value** from validation methods; don't validate one value and later use the original unvalidated value
7. **Handle SecurityException** gracefully - don't expose internal paths in error messages
8. **Log security violations** for monitoring

## Testing

Tests are located at:
- `src/test/java/io/github/carlos_emr/carlos/utility/PathValidationUtilsUnitTest.java`
- `src/test/java/io/github/carlos_emr/carlos/documentManager/IncomingDocUtilPathValidationTest.java`

Run tests with:
```bash
mvn -q -Dtest=PathValidationUtilsUnitTest,IncomingDocUtilPathValidationTest test
```

## CodeQL Model Pack (Static Analysis Integration)

`PathValidationUtils` methods are registered as CodeQL sanitizers via a custom model
pack at `.github/codeql/extensions/carlos-java-models/`. This teaches CodeQL that
taint from user-controlled arguments does **not** propagate through these methods to
their return values, preventing false-positive `java/path-injection` alerts.

### How It Works

The model pack uses the CodeQL **summaryModel** extensible to declare that the return
value of each `PathValidationUtils` method carries taint **only** from the safe
application-controlled directory argument (not from the user-provided filename). For
example, `validatePath(String userInput, File allowedDir)` maps `Argument[1]`
(allowedDir) → `ReturnValue`, deliberately omitting `Argument[0]` (userInput).

### What the Pack Contains

| Extensible | Description |
|---|---|
| `summaryModel` | PathValidationUtils methods: `validatePath`, `validatePathComponent`, `validateExistingPath`, `validateUpload` (2 overloads) |
| `summaryModel` | Wrapper methods that internally call PathValidationUtils (e.g., `MEDITECHHandler.validateAndGetFile`, `FaxManagerImpl.resolveAndValidateFilePath`, `NioFileManagerImpl.getOscarDocument`) |
| `neutralModel` | Boolean guard predicates: `isInAllowedTempDirectory`, `Util.isPathWithinDirectory`, `IncomingDocUtil.isPathWithinBounds` |
| `neutralModel` | Static sanitizer wrappers with no safe argument to model: `EDocUtil.resolvePath` |

### When Adding New PathValidationUtils Methods

If you add a new method to `PathValidationUtils`:

1. **Add a `summaryModel` entry** in `path-validation-sanitizers.yml` following the existing pattern
2. **Specify only the safe argument** as the taint source for `ReturnValue`
3. **Omit user-controlled arguments** from the flow specification

For validators like `validatePathComponent()` that return a checked user value rather than a
`File` under an allowed directory, model a non-user argument such as the fixed `label` as the
return source. This keeps CodeQL from treating the validated component as raw request taint while
the subsequent `validateExistingPath()` call still proves the final filesystem boundary.

### When Adding New Wrapper Methods

If you create a new method that internally calls `PathValidationUtils` to validate paths:

1. **Prefer using the return value** of `validateExistingPath`/`validatePath` — assign it back to the variable. This allows the existing model to cut taint without needing a new model entry.
2. **If using a try-catch + `isInAllowedTempDirectory` fallback pattern**, CodeQL cannot trace through the boolean guard. Either:
   - Add a `summaryModel` entry for the wrapper method in the model pack, OR
   - Add `// codeql[java/path-injection]` inline suppression at the file operation sink line

### Inline Suppression Pattern

For file operation sinks after boolean-guard validation patterns where the model can't
reach:

```java
// After validating with PathValidationUtils in a boolean-flag pattern:
BufferedReader br = new BufferedReader(new FileReader(file)); // codeql[java/path-injection] — validated by PathValidationUtils guard
```

## Related Documentation

- [OWASP Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal)
- [CWE-22: Improper Limitation of a Pathname](https://cwe.mitre.org/data/definitions/22.html)
- [CodeQL Model Packs](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning)
