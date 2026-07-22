package io.github.carlos_emr.carlos.utility;

import java.util.Arrays;

import io.github.carlos_emr.CarlosProperties;

/**
 * Configuration for certificate-backed PDF signatures.
 */
public final class PDFSigningConfig {
    static final String ENABLED_PROPERTY = "pdf.signing.enabled";
    static final String KEYSTORE_PATH_PROPERTY = "pdf.signing.keystore.path";
    static final String KEYSTORE_TYPE_PROPERTY = "pdf.signing.keystore.type";
    static final String KEYSTORE_PASSWORD_PROPERTY = "pdf.signing.keystore.password";
    static final String KEY_ALIAS_PROPERTY = "pdf.signing.key.alias";
    static final String KEY_PASSWORD_PROPERTY = "pdf.signing.key.password";
    static final String SIGNER_NAME_PROPERTY = "pdf.signing.signer.name";
    static final String REASON_PROPERTY = "pdf.signing.reason";
    static final String LOCATION_PROPERTY = "pdf.signing.location";
    static final String CONTACT_PROPERTY = "pdf.signing.contact";

    static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";
    static final String DEFAULT_SIGNER_NAME = "CARLOS EMR";
    static final String DEFAULT_REASON = "Signed by CARLOS EMR";

    private final boolean enabled;
    private final String keystorePath;
    private final String keystoreType;
    private final char[] keystorePassword;
    private final String keyAlias;
    private final char[] keyPassword;
    private final String signerName;
    private final String reason;
    private final String location;
    private final String contact;

    public PDFSigningConfig(
            boolean enabled,
            String keystorePath,
            String keystoreType,
            char[] keystorePassword,
            String keyAlias,
            char[] keyPassword,
            String signerName,
            String reason,
            String location,
            String contact
    ) {
        this.enabled = enabled;
        this.keystorePath = trimToNull(keystorePath);
        this.keystoreType = defaultIfBlank(keystoreType, DEFAULT_KEYSTORE_TYPE);
        this.keystorePassword = copyOrEmpty(keystorePassword);
        this.keyAlias = trimToNull(keyAlias);
        this.keyPassword = keyPassword == null ? null : Arrays.copyOf(keyPassword, keyPassword.length);
        this.signerName = defaultIfBlank(signerName, DEFAULT_SIGNER_NAME);
        this.reason = defaultIfBlank(reason, DEFAULT_REASON);
        this.location = trimToNull(location);
        this.contact = trimToNull(contact);
    }

    public static PDFSigningConfig fromCarlosProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        return new PDFSigningConfig(
                properties.isPropertyActive(ENABLED_PROPERTY),
                properties.getProperty(KEYSTORE_PATH_PROPERTY, ""),
                properties.getProperty(KEYSTORE_TYPE_PROPERTY, DEFAULT_KEYSTORE_TYPE),
                toPassword(properties.getProperty(KEYSTORE_PASSWORD_PROPERTY, "")),
                properties.getProperty(KEY_ALIAS_PROPERTY, ""),
                toOptionalPassword(properties.getProperty(KEY_PASSWORD_PROPERTY, "")),
                properties.getProperty(SIGNER_NAME_PROPERTY, DEFAULT_SIGNER_NAME),
                properties.getProperty(REASON_PROPERTY, DEFAULT_REASON),
                properties.getProperty(LOCATION_PROPERTY, ""),
                properties.getProperty(CONTACT_PROPERTY, "")
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public char[] getKeystorePassword() {
        return Arrays.copyOf(keystorePassword, keystorePassword.length);
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public char[] getKeyPassword() {
        char[] password = keyPassword != null ? keyPassword : keystorePassword;
        return Arrays.copyOf(password, password.length);
    }

    public String getSignerName() {
        return signerName;
    }

    public String getReason() {
        return reason;
    }

    public String getLocation() {
        return location;
    }

    public String getContact() {
        return contact;
    }

    public void validateEnabled() {
        if (!enabled) {
            return;
        }
        if (keystorePath == null) {
            throw new IllegalStateException("PDF signing is enabled but pdf.signing.keystore.path is not configured");
        }
        if (keyAlias == null) {
            throw new IllegalStateException("PDF signing is enabled but pdf.signing.key.alias is not configured");
        }
    }

    private static char[] toPassword(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static char[] toOptionalPassword(String value) {
        return trimToNull(value) == null ? null : value.toCharArray();
    }

    private static char[] copyOrEmpty(char[] value) {
        return value == null ? new char[0] : Arrays.copyOf(value, value.length);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
