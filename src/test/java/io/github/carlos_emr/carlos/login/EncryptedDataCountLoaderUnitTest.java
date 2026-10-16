/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.login.EncryptedDataCountLoader.Kind;
import io.github.carlos_emr.carlos.login.EncryptedDataCountLoader.Result;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.github.carlos_emr.carlos.login.EncryptedDataTestDatabase.encryptBytes;
import static io.github.carlos_emr.carlos.login.EncryptedDataTestDatabase.encryptText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the read-only count behind the #3939 startup guard against H2: what is and is not
 * recognised as ciphertext for each consumer of {@code encryption.util.secret.key}, tolerance of
 * tables and columns this schema lacks, and fail-closed reporting of anything else.
 *
 * <p>Ciphertext is produced under {@link EncryptedDataTestDatabase#SYNTHETIC_KEY}. {@code @Isolated}
 * because the prepared key in {@link EncryptionUtils} is process-global.</p>
 */
@Isolated
@Tag("unit")
@Tag("read")
class EncryptedDataCountLoaderUnitTest {

    private static final String PLAINTEXT_SECRET = "synthetic-plaintext-secret-not-real"; // NOSONAR java:S2068 - synthetic fixture

    private String originalKey;

    @BeforeEach
    void prepareSyntheticKey() {
        CarlosProperties properties = CarlosProperties.getInstance();
        originalKey = properties.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        properties.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptedDataTestDatabase.SYNTHETIC_KEY);
        EncryptionUtils.prepareSecretKeySpec();
    }

    @AfterEach
    void restoreKey() {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (originalKey != null) {
            properties.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalKey);
        } else {
            properties.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
        EncryptionUtils.prepareSecretKeySpec();
    }

    @Test
    @DisplayName("should count nothing on a database with no tables yet")
    void shouldCountNothing_whenDatabaseHasNoTables() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase()) {
            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.total()).isZero();
            assertThat(result.describeCounts()).isEqualTo("none");
        }
    }

    @Test
    @DisplayName("should count one record per consumer that holds ciphertext")
    void shouldCountEachConsumer_whenEveryKindHoldsCiphertext() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withAllTables()) {
            database.insertEmailConfig(1, "{\"host\":\"smtp.example.invalid\",\"password\":\""
                    + encryptText(PLAINTEXT_SECRET) + "\"}");
            database.insertEmailConfig(2, "{\"api_key\":\"" + encryptText(PLAINTEXT_SECRET) + "\"}");
            // Both fax passwords encrypted on one account still count as one account.
            database.insertFaxConfig(1, encryptText(PLAINTEXT_SECRET), encryptText(PLAINTEXT_SECRET));
            database.insertFaxConfig(2, "", encryptText(PLAINTEXT_SECRET));
            database.insertProperty(1, "teleplan_password", encryptText(PLAINTEXT_SECRET));
            database.insertSecurity(1, "synthetic-user-1", encryptText("JBSWY3DPEHPK3PXP"));
            // The SMS settings row with both its webhook secret and a credential encrypted is one record.
            database.insertSmsConfig(1, encryptText(PLAINTEXT_SECRET),
                    "{\"auth_token\":\"" + encryptText(PLAINTEXT_SECRET) + "\"}");
            database.insertDigitalSignature(1, encryptBytes(EncryptedDataTestDatabase.plaintextPng()));

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    Kind.EMAIL_CREDENTIALS, 2,
                    Kind.FAX_CREDENTIALS, 2,
                    Kind.TELEPLAN_CREDENTIALS, 1,
                    Kind.SMS_CREDENTIALS, 1,
                    Kind.MFA_SECRETS, 1,
                    Kind.DIGITAL_SIGNATURES, 1));
            assertThat(result.total()).isEqualTo(8);
            assertThat(result.describeCounts()).isEqualTo("email sender accounts: 2, fax accounts: 2,"
                    + " Teleplan passwords: 1, SMS settings: 1, users with an MFA secret: 1,"
                    + " stored digital signature images: 1");
        }
    }

    @Test
    @DisplayName("should not count plaintext credentials, near-miss markers or too-short signature values")
    void shouldCountNothing_whenValuesAreOnlyPlaintext() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withAllTables()) {
            // Legacy plaintext credentials, an unauthenticated relay, and the #3132 collision shapes:
            // a plaintext value that merely starts with {ENC}, a lower-case marker, a marker followed
            // by Base64 too short to hold an IV and tag, and {ENC} in a non-credential field.
            database.insertEmailConfig(1, "{\"password\":\"" + PLAINTEXT_SECRET + "\"}");
            database.insertEmailConfig(2, "{\"host\":\"localhost\",\"port\":\"25\"}");
            database.insertEmailConfig(3, "{\"password\":\"{ENC}not base64 at all\"}");
            database.insertEmailConfig(4, "{\"password\":\"{enc}" + encryptText(PLAINTEXT_SECRET).substring(5) + "\"}");
            database.insertEmailConfig(5, "{\"password\":\"{ENC}QUJDRA==\"}");
            database.insertEmailConfig(6, "{\"note\":\"" + encryptText(PLAINTEXT_SECRET) + "\"}");
            database.insertEmailConfig(7, "not json {ENC}");
            database.insertFaxConfig(1, PLAINTEXT_SECRET, "");
            database.insertFaxConfig(2, "", "{ENC}");
            database.insertProperty(1, "teleplan_password", PLAINTEXT_SECRET);
            // Ciphertext under another property name is not a Teleplan password.
            database.insertProperty(2, "some_other_setting", encryptText(PLAINTEXT_SECRET));
            database.insertSecurity(1, "synthetic-user-1", null);
            database.insertSecurity(2, "synthetic-user-2", "JBSWY3DPEHPK3PXP");
            // SMS settings with no secret, a marker too short to be ciphertext, plaintext credential
            // values, and a credentials value that is not a JSON object.
            database.insertSmsConfig(1, null, null);
            database.insertSmsConfig(2, "{ENC}QUJDRA==", "{\"account_sid\":\"" + PLAINTEXT_SECRET + "\"}");
            database.insertSmsConfig(3, PLAINTEXT_SECRET, "{\"note\":\"{ENC}\"}");
            database.insertSmsConfig(4, "", encryptText(PLAINTEXT_SECRET));
            // Too short to be ciphertext (less than an IV plus a GCM tag).
            database.insertDigitalSignature(3, new byte[]{1, 2, 3, 4, 5});

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).isEmpty();
            assertThat(result.total()).isZero();
        }
    }

    @Test
    @DisplayName("should say signatures are the only kind only when nothing else was found and every place was read")
    void shouldReportOnlySignatures_whenSignaturesAreAllThereIs() {
        assertThat(new EncryptedDataCountLoader.Result(Map.of(Kind.DIGITAL_SIGNATURES, 2), List.of())
                .onlySignatures()).isTrue();
        assertThat(new EncryptedDataCountLoader.Result(
                Map.of(Kind.DIGITAL_SIGNATURES, 2, Kind.FAX_CREDENTIALS, 1), List.of()).onlySignatures()).isFalse();
        assertThat(new EncryptedDataCountLoader.Result(
                Map.of(Kind.DIGITAL_SIGNATURES, 2), List.of("fax_config.passwd (SQLState 42000)")).onlySignatures())
                .isFalse();
        assertThat(new EncryptedDataCountLoader.Result(Map.of(), List.of()).onlySignatures()).isFalse();
    }

    @Test
    @DisplayName("should count ciphertext whose IV starts like an image header")
    void shouldCountCiphertext_whenItsIvMatchesAnImageHeader() throws Exception {
        byte[][] prefixes = {{0x42, 0x4d}, {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                {(byte) 0x89, 0x50, 0x4e, 0x47}, {0x47, 0x49, 0x46, 0x38}};
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withAllTables()) {
            for (int i = 0; i < prefixes.length; i++) {
                byte[] encrypted = EncryptedDataTestDatabase.encryptWithIvPrefix(prefixes[i]);
                assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo(EncryptedDataTestDatabase.plaintextPng());
                database.insertDigitalSignature(i + 1, encrypted);
            }
            assertThat(database.loader().load().counts()).containsEntry(Kind.DIGITAL_SIGNATURES, 4);
        }
    }

    @Test
    @DisplayName("should count every signature long enough to be ciphertext, plaintext images included")
    void shouldCountAmbiguousSignatures_whenLongEnoughForCiphertext() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withAllTables()) {
            database.insertDigitalSignature(1, EncryptedDataTestDatabase.plaintextPng());
            database.insertDigitalSignature(2, EncryptedDataTestDatabase.plaintextJpeg());
            database.insertDigitalSignature(3, new byte[28]);
            database.insertDigitalSignature(4, new byte[27]);
            database.insertDigitalSignature(5, new byte[0]);

            assertThat(database.loader().load().counts()).containsEntry(Kind.DIGITAL_SIGNATURES, 3);
        }
    }

    @Test
    @DisplayName("should count the SMS settings row from its webhook secret or any encrypted credential value")
    void shouldCountSmsSettings_whenWebhookSecretOrAnyCredentialIsEncrypted() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withAllTables()) {
            // Credential field names depend on the SMS provider, so any field counts.
            database.insertSmsConfig(1, null, "{\"account_sid\":\"AC-synthetic\",\"api_secret\":\""
                    + encryptText(PLAINTEXT_SECRET) + "\"}");
            database.insertSmsConfig(2, encryptText(PLAINTEXT_SECRET), null);

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).containsExactly(Map.entry(Kind.SMS_CREDENTIALS, 2));
            assertThat(result.describeCounts()).isEqualTo("SMS settings: 2");
        }
    }

    @Test
    @DisplayName("should count nothing for SMS settings on a schema from before the SMS settings page")
    void shouldCountNothing_whenSchemaPredatesSmsSettings() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withTablesBeforeSmsSettings();
             LogCapture capture = LogCapture.forLogger(EncryptedDataCountLoader.class)) {
            database.insertFaxConfig(1, "", "");

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.total()).isZero();
            assertThat(infoMessages(capture)).containsExactly("Encryption key check: not in this schema, counted"
                    + " as empty: sms_config.webhook_secret, sms_config.credentials");
        }
    }

    @Test
    @DisplayName("should treat a table this schema lacks as empty and still count the others")
    void shouldTolerateMissingTable_whenOtherTablesHoldCiphertext() throws Exception {
        // No property, security or DigitalSignature tables, as on a partial or older schema.
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withEmailConfig().withFaxConfig();
             LogCapture capture = LogCapture.forLogger(EncryptedDataCountLoader.class)) {
            database.insertFaxConfig(1, encryptText(PLAINTEXT_SECRET), "");

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).containsExactly(Map.entry(Kind.FAX_CREDENTIALS, 1));
            // One INFO line names every absent place, so a wrong, empty schema is visible in the log.
            assertThat(infoMessages(capture)).containsExactly("Encryption key check: not in this schema, counted"
                    + " as empty: property.value (teleplan_password), sms_config.webhook_secret,"
                    + " sms_config.credentials, security.mfaSecret, DigitalSignature.signatureImage");
        }
    }

    @Test
    @DisplayName("should treat a column this schema lacks as empty and still read the rest of the table")
    void shouldTolerateMissingColumn_whenOlderFaxSchemaLacksFaxPasswd() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withFaxConfigLackingFaxPasswd()) {
            database.execute("INSERT INTO fax_config (id, passwd) VALUES (1, '" + encryptText(PLAINTEXT_SECRET) + "')");

            Result result = database.loader().load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).containsExactly(Map.entry(Kind.FAX_CREDENTIALS, 1));
        }
    }

    @Test
    @DisplayName("should report a failed connection without its message, which can echo settings")
    void shouldReportIncomplete_whenConnectionCannotBeOpened() {
        EncryptedDataCountLoader loader = new EncryptedDataCountLoader(() -> {
            throw new SQLException("Access denied for " + EncryptedDataTestDatabase.DB_PASSWORD, "28000", 1045);
        });

        Result result = loader.load();

        assertThat(result.complete()).isFalse();
        assertThat(result.total()).isZero();
        assertThat(result.describeFailures())
                .isEqualTo("database connection (SQLState 28000, error 1045, java.sql.SQLException)")
                .doesNotContain(EncryptedDataTestDatabase.DB_PASSWORD);
    }

    @Test
    @DisplayName("should report every place that fails for a reason other than a missing table or column")
    void shouldReportIncomplete_whenQueriesFailForOtherReasons() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString()))
                .thenThrow(new SQLException("SELECT command denied: " + PLAINTEXT_SECRET, "42000", 1142));

        Result result = new EncryptedDataCountLoader(() -> connection).load();

        assertThat(result.complete()).isFalse();
        assertThat(result.failures()).hasSize(8)
                .allSatisfy(failure -> assertThat(failure)
                        .contains("SQLState 42000, error 1142")
                        .doesNotContain(PLAINTEXT_SECRET));
        assertThat(result.describeFailures())
                .contains("fax_config.passwd", "sms_config.credentials", "DigitalSignature.signatureImage");
        verify(connection).setReadOnly(true);
        verify(connection).close();
    }

    @Test
    @DisplayName("should treat MariaDB missing-table and missing-column states as empty, not as failures")
    void shouldTreatAbsentObjects_asEmptyForMariaDbStates() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString()))
                .thenThrow(new SQLException("Table doesn't exist", "42S02", 1146))
                .thenThrow(new SQLException("Unknown column", "42S22", 1054));

        Result result = new EncryptedDataCountLoader(() -> connection).load();

        assertThat(result.complete()).isTrue();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("should connect with the db_* settings the application data source uses")
    void shouldConnect_withApplicationDataSourceSettings() throws Exception {
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withSecurity()) {
            database.insertSecurity(1, "synthetic-user-1", encryptText("JBSWY3DPEHPK3PXP"));
            Properties properties = new Properties();
            database.applyTo(properties);

            Result result = EncryptedDataCountLoader.fromProperties(properties).load();

            assertThat(result.complete()).isTrue();
            assertThat(result.counts()).containsExactly(Map.entry(Kind.MFA_SECRETS, 1));
        }
    }

    @Test
    @DisplayName("should report missing connection settings as an incomplete check")
    void shouldReportIncomplete_whenConnectionSettingsAreMissing() {
        Result result = EncryptedDataCountLoader.fromProperties(new Properties()).load();

        assertThat(result.complete()).isFalse();
        assertThat(result.describeFailures()).isEqualTo("database connection (db_driver or db_uri is not set)");
    }

    @Test
    @DisplayName("should log no stored value, ciphertext or password while counting")
    void shouldLogNoSecretMaterial_whenCounting() throws Exception {
        String ciphertext = encryptText(PLAINTEXT_SECRET);
        try (EncryptedDataTestDatabase database = new EncryptedDataTestDatabase().withEmailConfig();
             LogCapture capture = LogCapture.forLogger(EncryptedDataCountLoader.class)) {
            database.insertEmailConfig(1, "{\"password\":\"" + ciphertext + "\"}");

            database.loader().load();

            // The absent tables are reported at INFO by location only.
            assertThat(capture.messages()).isNotEmpty()
                    .allSatisfy(message -> assertThat(message)
                            .doesNotContain(ciphertext, PLAINTEXT_SECRET, EncryptedDataTestDatabase.SYNTHETIC_KEY,
                                    EncryptedDataTestDatabase.DB_PASSWORD, "{ENC}"));
        }
    }

    private static List<String> infoMessages(LogCapture capture) {
        return capture.events().stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
    }
}
