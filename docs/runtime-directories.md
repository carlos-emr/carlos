# OpenO EMR Runtime Directories

## Overview

OpenO EMR requires several directories to exist at runtime with proper read/write permissions. These are **not** the same as classpath resources in `src/main/resources/` - these are filesystem directories where the application stores uploaded files, documents, exports, and other runtime data.

**Key Distinction:**
- **Classpath resources** (`src/main/resources/`) - Static files bundled with the application (forms, configs)
- **Runtime directories** - Filesystem locations where the application reads/writes data during operation

## Directory Properties

All directory properties are configured in `oscar.properties`. Many have **fallback logic** - if not explicitly set, they derive from `BASE_DOCUMENT_DIR`.

### Core Directories

| Property | Default Path | Purpose | Fallback |
|----------|--------------|---------|----------|
| `BASE_DOCUMENT_DIR` | `/var/lib/OscarDocument` | Base path for all document storage | None (required) |
| `DOCUMENT_DIR` | `{BASE}/oscar/document/` | Primary document storage | `BASE_DOCUMENT_DIR + "/document"` |
| `EFORM_IMAGES_DIR` | `{BASE}/oscar/eform/images/` | Uploaded eForm images | `BASE_DOCUMENT_DIR + "/eform/images"` |
| `DOCUMENT_CACHE_DIR` | - | Document cache directory | None |

### Billing Directories

| Property | Default Path | Purpose |
|----------|--------------|---------|
| `HOME_DIR` | `{BASE}/oscar/billing/download/` | Billing file downloads |
| `INVOICE_DIR` | `{BASE}/oscar/billing/invoices` | Invoice storage |

### Import/Export Directories

| Property | Default Path | Purpose |
|----------|--------------|---------|
| `INCOMINGDOCUMENT_DIR` | `{BASE}/oscar/incomingdocs` | Incoming document imports |
| `TMP_DIR` | `{BASE}/oscar/export/` | Temporary export files |
| `INTEGRATOR_OUTPUT_DIR` | `/var/lib/bc-integrator/export` | BC Integrator export files |

### HL7 Directories (Optional)

| Property | Purpose |
|----------|---------|
| `hl7_a04_build_dir` | HL7 A04 message build directory |
| `hl7_a04_sent_dir` | HL7 A04 sent messages |
| `hl7_a04_fail_dir` | HL7 A04 failed messages |
| `spire_download_dir` | Spire integration downloads |

## Permissions Requirements

All runtime directories require:
- **Owner**: The user running Tomcat (typically `tomcat` or `www-data`)
- **Permissions**: Read and write access (typically `755` or `775`)

### Example Setup

```bash
# Create base directory structure
sudo mkdir -p /var/lib/OscarDocument/oscar/{document,eform/images,billing/{download,invoices},incomingdocs,export}

# Set ownership to tomcat user
sudo chown -R tomcat:tomcat /var/lib/OscarDocument

# Set permissions
sudo chmod -R 755 /var/lib/OscarDocument
```

## Fallback Logic

The application uses fallback logic for commonly used directories. From `OscarProperties.java`:

```java
// DOCUMENT_DIR fallback
public String getDocumentDirectory() {
    String documents = getProperty("DOCUMENT_DIR");
    if (documents == null) {
        documents = Paths.get(getProperty("BASE_DOCUMENT_DIR"), "document").toString();
    }
    return documents;
}

// EFORM_IMAGES_DIR fallback
public String getEformImageDirectory() {
    String eform_images = getProperty("EFORM_IMAGES_DIR");
    if (eform_images == null) {
        eform_images = Paths.get(getProperty("BASE_DOCUMENT_DIR"), "eform", "images").toString();
    }
    return eform_images;
}
```

This means you can often just set `BASE_DOCUMENT_DIR` and the application will create appropriate subdirectories.

## Troubleshooting

### Common Errors

**"Could not create directory"**
- The parent directory doesn't exist
- Tomcat user doesn't have write permission
- Check `BASE_DOCUMENT_DIR` is set correctly

**"Upload failed: unable to prepare upload directory"**
- The specific directory (e.g., `EFORM_IMAGES_DIR`) doesn't exist
- Application couldn't create it automatically
- Manually create the directory with proper permissions

**Permission Denied**
- Tomcat user doesn't own the directory
- Directory permissions are too restrictive
- Run: `sudo chown -R tomcat:tomcat /var/lib/OscarDocument`

### Verification

Check directory configuration:
```bash
# View current properties
grep -E "_DIR|_PATH" /path/to/oscar.properties

# Verify directories exist
ls -la /var/lib/OscarDocument/

# Check Tomcat user can write
sudo -u tomcat touch /var/lib/OscarDocument/test && rm /var/lib/OscarDocument/test
```

## DevContainer Configuration

In the development environment, directories are pre-configured in:
`.devcontainer/development/config/shared/volumes/oscar.properties`

```properties
BASE_DOCUMENT_DIR=/var/lib/OscarDocument
DOCUMENT_DIR=/var/lib/OscarDocument/oscar/document/
EFORM_IMAGES_DIR=/var/lib/OscarDocument/oscar/eform/images/
HOME_DIR=/var/lib/OscarDocument/oscar/billing/download/
INVOICE_DIR=/var/lib/OscarDocument/oscar/billing/invoices
INCOMINGDOCUMENT_DIR=/var/lib/OscarDocument/oscar/incomingdocs
```

The DevContainer automatically creates these directories with appropriate permissions.

## Related Code

Key files that use these directories:
- `ca.openosp.OscarProperties` - Directory property accessors
- `ca.openosp.openo.eform.upload.ImageUpload2Action` - Uses `EFORM_IMAGES_DIR`
- `ca.openosp.openo.documentManager.*` - Uses `DOCUMENT_DIR`

## Related Documentation

- [Resources Directory Overview](resources-directory.md) - Classpath resources (different from runtime directories)
- [oscar_mcmaster.properties](../src/main/resources/oscar_mcmaster.properties) - Default property values with comments
