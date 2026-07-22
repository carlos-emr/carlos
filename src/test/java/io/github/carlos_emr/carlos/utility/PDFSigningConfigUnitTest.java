/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("PDFSigningConfig")
class PDFSigningConfigUnitTest {

    @Test
    @DisplayName("should create config from Carlos properties")
    void shouldCreateConfigFromCarlosProperties() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(PDFSigningConfig.ENABLED_PROPERTY, "YES");
        overrides.put(PDFSigningConfig.KEYSTORE_PATH_PROPERTY, " /secure/signing.p12 ");
        overrides.put(PDFSigningConfig.KEYSTORE_TYPE_PROPERTY, "JKS");
        overrides.put(PDFSigningConfig.KEYSTORE_PASSWORD_PROPERTY, "store-secret");
        overrides.put(PDFSigningConfig.KEY_ALIAS_PROPERTY, " clinic-key ");
        overrides.put(PDFSigningConfig.KEY_PASSWORD_PROPERTY, "key-secret");
        overrides.put(PDFSigningConfig.SIGNER_NAME_PROPERTY, "Clinic Signer");
        overrides.put(PDFSigningConfig.REASON_PROPERTY, "Approved");
        overrides.put(PDFSigningConfig.LOCATION_PROPERTY, "Clinic");
        overrides.put(PDFSigningConfig.CONTACT_PROPERTY, "contact@example.com");

        withCarlosProperties(overrides, () -> {
            PDFSigningConfig config = PDFSigningConfig.fromCarlosProperties();

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getKeystorePath()).isEqualTo("/secure/signing.p12");
            assertThat(config.getKeystoreType()).isEqualTo("JKS");
            assertThat(config.getKeystorePassword()).containsExactly("store-secret".toCharArray());
            assertThat(config.getKeyAlias()).isEqualTo("clinic-key");
            assertThat(config.getKeyPassword()).containsExactly("key-secret".toCharArray());
            assertThat(config.getSignerName()).isEqualTo("Clinic Signer");
            assertThat(config.getReason()).isEqualTo("Approved");
            assertThat(config.getLocation()).isEqualTo("Clinic");
            assertThat(config.getContact()).isEqualTo("contact@example.com");
        });
    }

    @Test
    @DisplayName("should trim optional values and apply signing defaults")
    void shouldTrimOptionalValuesAndApplySigningDefaults() {
        PDFSigningConfig config = new PDFSigningConfig(
                true,
                " /tmp/pdf-signing.p12 ",
                " ",
                "keystore-password".toCharArray(),
                " signing-key ",
                null,
                " ",
                null,
                " ",
                " contact@example.com ");

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getKeystorePath()).isEqualTo("/tmp/pdf-signing.p12");
        assertThat(config.getKeystoreType()).isEqualTo("PKCS12");
        assertThat(config.getKeyAlias()).isEqualTo("signing-key");
        assertThat(config.getSignerName()).isEqualTo("CARLOS EMR");
        assertThat(config.getReason()).isEqualTo("Signed by CARLOS EMR");
        assertThat(config.getLocation()).isNull();
        assertThat(config.getContact()).isEqualTo("contact@example.com");
        assertThat(config.getKeyPassword()).containsExactly("keystore-password".toCharArray());
    }

    @Test
    @DisplayName("should defensively copy credential arrays")
    void shouldDefensivelyCopyCredentialArrays() {
        char[] keystorePassword = "keystore-password".toCharArray();
        char[] keyPassword = "key-password".toCharArray();
        PDFSigningConfig config = new PDFSigningConfig(
                true,
                "/tmp/pdf-signing.p12",
                "PKCS12",
                keystorePassword,
                "signing-key",
                keyPassword,
                "Signer",
                "Reason",
                "Location",
                "Contact");

        keystorePassword[0] = 'x';
        keyPassword[0] = 'y';
        char[] returnedKeystorePassword = config.getKeystorePassword();
        char[] returnedKeyPassword = config.getKeyPassword();
        returnedKeystorePassword[0] = 'z';
        returnedKeyPassword[0] = 'z';

        assertThat(config.getKeystorePassword()).containsExactly("keystore-password".toCharArray());
        assertThat(config.getKeyPassword()).containsExactly("key-password".toCharArray());
    }

    @Test
    @DisplayName("should validate required fields only when signing is enabled")
    void shouldValidateRequiredFieldsOnlyWhenSigningEnabled() {
        PDFSigningConfig disabledConfig = new PDFSigningConfig(
                false, null, null, null, null, null, null, null, null, null);
        PDFSigningConfig missingPath = new PDFSigningConfig(
                true, " ", "PKCS12", new char[0], "signing-key", null, null, null, null, null);
        PDFSigningConfig missingAlias = new PDFSigningConfig(
                true, "/tmp/pdf-signing.p12", "PKCS12", new char[0], " ", null, null, null, null, null);

        assertThatCode(disabledConfig::validateEnabled).doesNotThrowAnyException();
        assertThatThrownBy(missingPath::validateEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.signing.keystore.path");
        assertThatThrownBy(missingAlias::validateEnabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pdf.signing.key.alias");
    }

    private static void withCarlosProperties(Map<String, String> overrides, Runnable assertions) {
        CarlosProperties properties = CarlosProperties.getInstance();
        Map<String, Object> originalValues = new HashMap<>();
        overrides.keySet().forEach(key -> originalValues.put(key, properties.get(key)));
        try {
            overrides.forEach(properties::setProperty);
            assertions.run();
        } finally {
            originalValues.forEach((key, value) -> {
                if (value == null) {
                    properties.remove(key);
                } else {
                    properties.put(key, value);
                }
            });
        }
    }
}
