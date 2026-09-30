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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static io.github.carlos_emr.carlos.login.EncryptedDataTestDatabase.encryptBytes;
import static io.github.carlos_emr.carlos.login.EncryptedDataTestDatabase.encryptText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives {@link Startup#contextInitialized} for #3939: a missing {@code encryption.util.secret.key}
 * is replaced by a generated key only on a fresh install or when the operator acknowledges the loss.
 * The encrypted-data check runs against a private in-memory H2 database through the same
 * {@code db_*} settings the application uses.
 *
 * <p>Every test mutates process-global state (the {@link CarlosProperties} singleton, the prepared
 * key in {@link EncryptionUtils}, {@code user.home}); {@code @Isolated} keeps them from running
 * alongside other tests, and {@link #restoreGlobals()} puts everything back.</p>
 */
@Isolated
@Tag("unit")
@Tag("create")
class StartupEncryptionKeyGuardUnitTest {

    private static final String PLAINTEXT_SECRET = "synthetic-plaintext-secret-not-real"; // NOSONAR java:S2068 - synthetic fixture
    private static final String ACK = Startup.ACKNOWLEDGE_KEY_LOSS_PROPERTY;
    private static final String KEY = EncryptionUtils.SECRET_KEY_ENV_VAR;

    @TempDir
    Path tempDir;

    private final CarlosProperties props = CarlosProperties.getInstance();
    private final Properties snapshot = new Properties();
    private final List<String> secretMaterial = new ArrayList<>();
    private String originalUserHome;
    private Object originalKeySpec;
    private Field keySpecField;
    private EncryptedDataTestDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        snapshot.putAll(props);
        originalUserHome = System.getProperty("user.home");
        keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        originalKeySpec = keySpecField.get(null);

        System.setProperty("user.home", tempDir.toString());
        database = new EncryptedDataTestDatabase();
        database.applyTo(props);
        props.remove(ACK);
        // Keep Startup from creating document directories outside the temp dir.
        props.remove("BASE_DOCUMENT_DIR");

        // Fixtures are encrypted under the synthetic key, which is then "lost" before startup.
        props.setProperty(KEY, EncryptedDataTestDatabase.SYNTHETIC_KEY);
        EncryptionUtils.prepareSecretKeySpec();
        secretMaterial.add(EncryptedDataTestDatabase.SYNTHETIC_KEY);
        secretMaterial.add(EncryptedDataTestDatabase.DB_PASSWORD);
        secretMaterial.add(PLAINTEXT_SECRET);
    }

    @AfterEach
    void restoreGlobals() throws Exception {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        } else {
            System.clearProperty("user.home");
        }
        props.clear();
        props.putAll(snapshot);
        keySpecField.set(null, originalKeySpec);
        database.close();
    }

    @Test
    @DisplayName("should generate and save a key on a fresh install with no encrypted data")
    void shouldGenerateKey_whenFreshInstallHasNoEncryptedData() throws Exception {
        database.withAllTables();
        // A fresh ON seed: one fax account with empty passwords, plus legacy plaintext credentials,
        // which a new key does not orphan.
        database.insertFaxConfig(1, "", "");
        database.insertEmailConfig(1, "{\"password\":\"" + PLAINTEXT_SECRET + "\"}");
        loseKey();

        try (StartupLogs logs = new StartupLogs()) {
            new Startup().contextInitialized(newStartupEvent());

            String generated = props.getProperty(KEY);
            assertThat(Base64.getDecoder().decode(generated)).hasSize(32);
            assertThat(generated).isNotEqualTo(EncryptedDataTestDatabase.SYNTHETIC_KEY);
            assertThat(savedPropertiesFile()).exists();
            assertThat(Files.readString(savedPropertiesFile())).contains(KEY + "=" + generated);
            assertThat(EncryptionUtils.decrypt(EncryptionUtils.encrypt("round-trip"))).isEqualTo("round-trip");
            assertThat(logs.startupMessages()).contains("New Secret Key generated...");
            assertThat(logs.errors()).isEmpty();
            secretMaterial.add(generated);
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should generate a key when the schema has no tables yet")
    void shouldGenerateKey_whenDatabaseHasNoTablesYet() throws Exception {
        loseKey();

        new Startup().contextInitialized(newStartupEvent());

        assertThat(props.getProperty(KEY)).isNotBlank();
        assertThat(savedPropertiesFile()).exists();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Kind.class)
    @DisplayName("should refuse to start when one kind of encrypted data exists and the key is missing")
    void shouldRefuseStartup_whenOnlyOneKindIsEncrypted(Kind kind) throws Exception {
        database.withAllTables();
        seed(kind);
        loseKey();

        try (StartupLogs logs = new StartupLogs()) {
            Startup startup = new Startup();
            ServletContextEvent event = newStartupEvent();
            assertThatThrownBy(() -> startup.contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .cause()
                    .hasMessageContaining(KEY + " is missing or blank, but 1 items in the database are encrypted")
                    .hasMessageContaining(kind.label() + ": 1")
                    .hasMessageContaining("restore the original " + KEY + " from backup")
                    .hasMessageContaining(ACK + "=true");

            // Nothing was generated or written, and the operator got exactly one ERROR.
            assertThat(props.getProperty(KEY)).isBlank();
            assertThat(savedPropertiesFile()).doesNotExist();
            assertThat(logs.errors()).hasSize(1);
            assertThat(logs.errors().get(0))
                    .startsWith(KEY + " is missing or blank, but 1 items in the database are encrypted")
                    .contains(kind.label() + ": 1", kind.remedy());
            assertThat(logs.startupMessages()).doesNotContain("Unexpected error.", "New Secret Key generated...");
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should list every kind with its count in one refusal")
    void shouldListEveryKind_whenSeveralKindsAreEncrypted() throws Exception {
        database.withAllTables();
        for (Kind kind : Kind.values()) {
            seed(kind);
        }
        loseKey();

        try (StartupLogs logs = new StartupLogs()) {
            Startup startup = new Startup();
            ServletContextEvent event = newStartupEvent();
            assertThatThrownBy(() -> startup.contextInitialized(event))
                    .isInstanceOf(RuntimeException.class);

            assertThat(logs.errors()).containsExactly(KEY + " is missing or blank, but 5 items in the database"
                    + " are encrypted with the original key (email sender accounts: 1, fax accounts: 1,"
                    + " Teleplan passwords: 1, users with an MFA secret: 1, stored digital signature images: 1)."
                    + " Refusing to start: a new key cannot decrypt them."
                    + " Fix: restore the original " + KEY + " from backup into the properties file, then restart."
                    + " Only if the original key is lost for good: set " + ACK + "=true and restart."
                    + " CARLOS then generates a new key and everything encrypted with the old key stays unreadable"
                    + " (re-enter the SMTP password or API key of each email sender account;"
                    + " re-enter each fax account's password in Administration > Faxes > Configure Fax;"
                    + " re-enter the Teleplan password;"
                    + " reset MFA for each of those users, who cannot log in until it is reset;"
                    + " those signature images cannot be recovered).");
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should generate a key and log one ERROR with counts when the loss is acknowledged")
    void shouldGenerateKeyAndLogCounts_whenLossIsAcknowledged() throws Exception {
        database.withAllTables();
        for (Kind kind : Kind.values()) {
            seed(kind);
        }
        loseKey();
        props.setProperty(ACK, "true");

        try (StartupLogs logs = new StartupLogs()) {
            new Startup().contextInitialized(newStartupEvent());

            String generated = props.getProperty(KEY);
            assertThat(generated).isNotBlank().isNotEqualTo(EncryptedDataTestDatabase.SYNTHETIC_KEY);
            assertThat(Files.readString(savedPropertiesFile())).contains(KEY + "=" + generated);
            assertThat(logs.errors()).containsExactly(ACK + " is set: generated a new " + KEY
                    + " over 5 encrypted items that are now unreadable (email sender accounts: 1, fax accounts: 1,"
                    + " Teleplan passwords: 1, users with an MFA secret: 1, stored digital signature images: 1)."
                    + " Now: re-enter the SMTP password or API key of each email sender account;"
                    + " re-enter each fax account's password in Administration > Faxes > Configure Fax;"
                    + " re-enter the Teleplan password;"
                    + " reset MFA for each of those users, who cannot log in until it is reset;"
                    + " those signature images cannot be recovered."
                    + " Then remove " + ACK + " from the properties file.");
            assertThat(logs.startupMessages()).doesNotContain("New Secret Key generated...");
            secretMaterial.add(generated);
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"true", "YES", " on "})
    @DisplayName("should accept the acknowledgement the way other boolean properties are matched")
    void shouldGenerateKey_whenAcknowledgementUsesAnActiveMarker(String value) throws Exception {
        database.withAllTables();
        seed(Kind.FAX_CREDENTIALS);
        loseKey();
        props.setProperty(ACK, value);

        new Startup().contextInitialized(newStartupEvent());

        assertThat(props.getProperty(KEY)).isNotBlank();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"false", "1", "", "acknowledge"})
    @DisplayName("should keep refusing when the acknowledgement is not an active marker")
    void shouldRefuseStartup_whenAcknowledgementIsNotActive(String value) throws Exception {
        database.withAllTables();
        seed(Kind.EMAIL_CREDENTIALS);
        loseKey();
        props.setProperty(ACK, value);

        Startup startup = new Startup();

        ServletContextEvent event = newStartupEvent();

        assertThatThrownBy(() -> startup.contextInitialized(event))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(props.getProperty(KEY)).isBlank();
    }

    @Test
    @DisplayName("should fail closed when the encrypted-data check cannot reach the database")
    void shouldRefuseStartup_whenCheckCannotConnect() throws Exception {
        // no driver accepts this URL, so the check cannot connect
        props.setProperty("db_uri", "jdbc:carlos-no-such-driver:");
        loseKey();

        try (StartupLogs logs = new StartupLogs()) {
            Startup startup = new Startup();
            ServletContextEvent event = newStartupEvent();
            assertThatThrownBy(() -> startup.contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("could not check whether the database holds data encrypted")
                    .hasMessageContaining("database connection (SQLState 08001, error 0, java.sql.SQLException)")
                    .hasMessageContaining("fix that and restart so the check can run")
                    .hasMessageContaining(ACK + "=true");

            assertThat(props.getProperty(KEY)).isBlank();
            assertThat(savedPropertiesFile()).doesNotExist();
            assertThat(logs.errors()).hasSize(1);
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should generate a key and say the count is incomplete when the check fails but the loss is acknowledged")
    void shouldGenerateKey_whenCheckFailsButLossIsAcknowledged() throws Exception {
        // no driver accepts this URL, so the check cannot connect
        props.setProperty("db_uri", "jdbc:carlos-no-such-driver:");
        loseKey();
        props.setProperty(ACK, "yes");

        try (StartupLogs logs = new StartupLogs()) {
            new Startup().contextInitialized(newStartupEvent());

            assertThat(props.getProperty(KEY)).isNotBlank();
            assertThat(logs.errors()).containsExactly(ACK + " is set: generated a new " + KEY
                    + ". Any data encrypted with the old key is now unreadable. Found 0 items (none), but could not"
                    + " read database connection (SQLState 08001, error 0, java.sql.SQLException), so there may be more."
                    + " Then remove " + ACK + " from the properties file.");
            secretMaterial.add(props.getProperty(KEY));
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should still refuse an invalid key, without scanning or regenerating, even when the loss is acknowledged")
    void shouldRefuseInvalidKey_whenLossIsAcknowledged() throws Exception {
        database.withAllTables();
        seed(Kind.MFA_SECRETS);
        String invalidKey = "not-base64%%";
        props.setProperty(KEY, invalidKey);
        props.setProperty(ACK, "true");
        keySpecField.set(null, null);

        try (StartupLogs logs = new StartupLogs()) {
            Startup startup = new Startup();
            ServletContextEvent event = newStartupEvent();
            assertThatThrownBy(() -> startup.contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .cause()
                    .hasMessageStartingWith("Configured encryption key is invalid")
                    .hasMessageContaining("refusing to start");

            assertThat(props.getProperty(KEY)).isEqualTo(invalidKey);
            assertThat(keySpecField.get(null)).isNull();
            assertThat(savedPropertiesFile()).doesNotExist();
            // Unchanged invalid-key path: the generic startup error, not the missing-key refusal.
            assertThat(logs.startupMessages()).contains("Unexpected error.");
            assertThat(logs.startupMessages()).noneMatch(message -> message.contains("is missing or blank"));
        }
    }

    @Test
    @DisplayName("should warn that the acknowledgement is inert while a valid key is configured")
    void shouldWarnAboutAcknowledgement_whenKeyIsConfigured() throws Exception {
        props.setProperty(ACK, "true");

        try (StartupLogs logs = new StartupLogs()) {
            new Startup().contextInitialized(newStartupEvent());

            assertThat(props.getProperty(KEY)).isEqualTo(EncryptedDataTestDatabase.SYNTHETIC_KEY);
            assertThat(savedPropertiesFile()).doesNotExist();
            assertThat(logs.warnings()).containsExactly(ACK + " is set but has no effect while " + KEY
                    + " is configured. Remove it, so that a future loss of the key stops startup instead of being accepted.");
            logs.assertNoSecretMaterial(secretMaterial);
        }
    }

    @Test
    @DisplayName("should generate a key and warn that the acknowledgement was unused on a fresh install")
    void shouldWarnAboutAcknowledgement_whenNothingWasEncrypted() throws Exception {
        database.withAllTables();
        loseKey();
        props.setProperty(ACK, "on");

        try (StartupLogs logs = new StartupLogs()) {
            new Startup().contextInitialized(newStartupEvent());

            assertThat(props.getProperty(KEY)).isNotBlank();
            assertThat(logs.errors()).isEmpty();
            assertThat(logs.warnings()).containsExactly(ACK + " is set but nothing encrypted was found, so no data"
                    + " was lost. Remove it, so that a future loss of the key stops startup instead of being accepted.");
        }
    }

    @Test
    @DisplayName("should refuse over encrypted fax data when the schema lacks other tables and columns")
    void shouldRefuseStartup_whenOnlyPartOfTheSchemaExists() throws Exception {
        // An older schema: no faxPasswd column, and none of the other tables.
        database.withFaxConfigLackingFaxPasswd();
        database.execute("INSERT INTO fax_config (id, passwd) VALUES (1, '" + encryptText(PLAINTEXT_SECRET) + "')");
        loseKey();

        Startup startup = new Startup();

        ServletContextEvent event = newStartupEvent();

        assertThatThrownBy(() -> startup.contextInitialized(event))
                .isInstanceOf(RuntimeException.class)
                .cause()
                .hasMessageContaining("but 1 items in the database are encrypted with the original key (fax accounts: 1)");
    }

    /** Stores one record of the given kind, encrypted under the synthetic key. */
    private void seed(Kind kind) throws Exception {
        String ciphertext = encryptText(PLAINTEXT_SECRET);
        secretMaterial.add(ciphertext);
        switch (kind) {
            case EMAIL_CREDENTIALS -> database.insertEmailConfig(1, "{\"password\":\"" + ciphertext + "\"}");
            case FAX_CREDENTIALS -> database.insertFaxConfig(1, "", ciphertext);
            case TELEPLAN_CREDENTIALS -> database.insertProperty(1, "teleplan_password", ciphertext);
            case MFA_SECRETS -> database.insertSecurity(1, "synthetic-user-1", ciphertext);
            case DIGITAL_SIGNATURES -> {
                byte[] image = encryptBytes(EncryptedDataTestDatabase.plaintextPng());
                secretMaterial.add(Base64.getEncoder().encodeToString(image));
                secretMaterial.add(new String(image, StandardCharsets.ISO_8859_1));
                database.insertDigitalSignature(1, image);
            }
        }
    }

    /** The key goes missing: blank in the properties, and nothing prepared. */
    private void loseKey() throws Exception {
        props.setProperty(KEY, "   ");
        keySpecField.set(null, null);
    }

    private Path savedPropertiesFile() {
        return tempDir.resolve("carlos.properties");
    }

    private ServletContextEvent newStartupEvent() throws Exception {
        Path webappRoot = tempDir.resolve("webapps").resolve("carlos");
        Files.createDirectories(webappRoot);
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(event.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResource("/")).thenReturn(webappRoot.toUri().toURL());
        return event;
    }

    /** Captures every logger that sees the key, the database settings or the stored values. */
    private static final class StartupLogs implements AutoCloseable {
        private final LogCapture startup = LogCapture.forLogger(Startup.class);
        private final LogCapture loader = LogCapture.forLogger(EncryptedDataCountLoader.class);
        private final LogCapture encryption = LogCapture.forLogger(EncryptionUtils.class);
        private final LogCapture properties = LogCapture.forLogger(CarlosProperties.class);

        List<String> startupMessages() {
            return startup.messages();
        }

        List<String> errors() {
            return atLevel(Level.ERROR);
        }

        List<String> warnings() {
            return atLevel(Level.WARN);
        }

        private List<String> atLevel(Level level) {
            return startup.events().stream()
                    .filter(event -> event.getLevel() == level)
                    .map(event -> event.getMessage().getFormattedMessage())
                    .toList();
        }

        void assertNoSecretMaterial(List<String> secrets) {
            List<LogEvent> events = new ArrayList<>();
            for (LogCapture capture : List.of(startup, loader, encryption, properties)) {
                events.addAll(capture.events());
            }
            assertThat(events).allSatisfy(event -> {
                String rendered = event.getMessage().getFormattedMessage()
                        + (event.getThrown() == null ? "" : " " + throwableText(event.getThrown()));
                for (String secret : secrets) {
                    assertThat(rendered).doesNotContain(secret);
                }
                assertThat(rendered).doesNotContain("{ENC}");
            });
        }

        private static String throwableText(Throwable thrown) {
            StringBuilder text = new StringBuilder();
            for (Throwable current = thrown; current != null; current = current.getCause()) {
                text.append(current).append(' ');
            }
            return text.toString();
        }

        @Override
        public void close() {
            properties.close();
            encryption.close();
            loader.close();
            startup.close();
        }
    }
}
