package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.WebappShutdownResources;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every test here mutates process-global state (the static {@code EncryptionUtils.SECRET_KEY_SPEC},
 * the {@code CarlosProperties} singleton, and the {@code user.home} system property). {@code @Isolated}
 * makes that explicit so these never run concurrently with other tests under parallel Surefire.
 */
@Isolated
@Tag("unit")
class StartupUnitTest extends CarlosUnitTestBase {

    @Test
    @Tag("delete")
    void shouldUseThreadContextClassLoader_whenDestroyedWithNullEvent() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = new ClassLoader(original) {
        };

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(fallback))
                    .thenReturn(shutdownReport());
            Thread.currentThread().setContextClassLoader(fallback);

            new Startup().contextDestroyed(null);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(fallback));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    @Tag("delete")
    void shouldUseThreadContextClassLoader_whenServletContextIsNull() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader fallback = new ClassLoader(original) {
        };
        ServletContextEvent event = mock(ServletContextEvent.class);

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(fallback))
                    .thenReturn(shutdownReport());
            Thread.currentThread().setContextClassLoader(fallback);

            new Startup().contextDestroyed(event);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(fallback));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    @Tag("delete")
    void shouldUseServletContextClassLoader_whenAvailable() throws Exception {
        ClassLoader webappClassLoader = new ClassLoader(getClass().getClassLoader()) {
        };
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        org.mockito.Mockito.when(event.getServletContext()).thenReturn(servletContext);
        org.mockito.Mockito.when(servletContext.getClassLoader()).thenReturn(webappClassLoader);

        try (MockedStatic<WebappShutdownResources> shutdown = mockStatic(WebappShutdownResources.class)) {
            shutdown.when(() -> WebappShutdownResources.releaseForContext(webappClassLoader))
                    .thenReturn(shutdownReport());

            new Startup().contextDestroyed(event);

            shutdown.verify(() -> WebappShutdownResources.releaseForContext(webappClassLoader));
        }

        verify(event).getServletContext();
    }

    private WebappShutdownResources.ShutdownReport shutdownReport() throws Exception {
        Constructor<WebappShutdownResources.ShutdownReport> constructor =
                WebappShutdownResources.ShutdownReport.class.getDeclaredConstructor(List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(List.of());
    }

    @Test
    @Tag("create")
    @DisplayName("should encrypt successfully after prepareSecretKeySpec reinitializes a null key")
    void shouldEncryptSuccessfully_afterPrepareSecretKeySpecReinitializesNullKey() throws Exception {
        // Simulate EncryptionUtils loaded before properties: null out SECRET_KEY_SPEC
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        try {
            keySpecField.set(null, null);

            // Verify encryption fails with null key
            assertThatCode(() -> EncryptionUtils.encrypt("test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secret key not found");

            // Generate a valid key and set it in CarlosProperties
            String validKey = EncryptionUtils.generateSecretKey();
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, validKey);

            // Reinitialize - simulates what Startup does after ensuring key exists
            EncryptionUtils.prepareSecretKeySpec();

            // Verify encryption now succeeds
            String encrypted = EncryptionUtils.encrypt("test-password");
            assertThat(encrypted).startsWith("{ENC}");

            // Verify round-trip works
            String decrypted = EncryptionUtils.decrypt(encrypted);
            assertThat(decrypted).isEqualTo("test-password");
        } finally {
            // Restore original property and key spec to avoid polluting other tests
            if (originalProp != null) {
                props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
            } else {
                props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
            }
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should handle blank key as missing in prepareSecretKeySpec")
    void shouldHandleBlankKey_asMissingInPrepareSecretKeySpec() throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        try {
            keySpecField.set(null, null);

            // Set a blank key - prepareSecretKeySpec should treat it as missing
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "   ");

            // prepareSecretKeySpec should handle the blank key gracefully
            assertThatCode(() -> EncryptionUtils.prepareSecretKeySpec())
                    .doesNotThrowAnyException();

            // SECRET_KEY_SPEC should remain null (blank treated as missing)
            assertThat(keySpecField.get(null)).isNull();
        } finally {
            // Restore original state, removing the property entirely if it was previously unset
            if (originalProp != null) {
                props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
            } else {
                props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
            }
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should clear prepared key when configured key is invalid")
    void shouldClearPreparedKey_whenConfiguredKeyIsInvalid() throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        try {
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
            EncryptionUtils.prepareSecretKeySpec();
            assertThat(keySpecField.get(null)).isNotNull();

            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "AA==");
            assertThatCode(() -> EncryptionUtils.prepareSecretKeySpec())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid AES key length");
            assertThat(keySpecField.get(null)).isNull();

            // Near-miss length (33 bytes) - valid Base64 but not a 16/24/32-byte AES key,
            // the realistic truncation/corruption case.
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR,
                    Base64.getEncoder().encodeToString(new byte[33]));
            assertThatCode(() -> EncryptionUtils.prepareSecretKeySpec())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid AES key length");
            assertThat(keySpecField.get(null)).isNull();

            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "not-base64%%");
            assertThatCode(() -> EncryptionUtils.prepareSecretKeySpec())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(keySpecField.get(null)).isNull();

            // Recovery: an invalid key must not permanently poison the class - a subsequent valid
            // key still prepares successfully.
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
            EncryptionUtils.prepareSecretKeySpec();
            assertThat(keySpecField.get(null)).isNotNull();
        } finally {
            if (originalProp != null) {
                props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
            } else {
                props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
            }
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should prepare key when configured key has surrounding whitespace")
    void shouldPrepareKey_whenConfiguredKeyHasSurroundingWhitespace() throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        try {
            keySpecField.set(null, null);

            // A valid Base64 key with incidental leading/trailing whitespace (e.g. a manual
            // properties edit) must still prepare - Base64.getDecoder() would otherwise reject it
            // and abort startup. Without the trim in prepareSecretKeySpec this throws.
            String validKey = EncryptionUtils.generateSecretKey();
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "  " + validKey + "\n");

            assertThatCode(EncryptionUtils::prepareSecretKeySpec).doesNotThrowAnyException();
            assertThat(keySpecField.get(null)).isNotNull();

            String encrypted = EncryptionUtils.encrypt("whitespace-key-password");
            assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("whitespace-key-password");
        } finally {
            restoreProperty(props, originalProp);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 24, 32})
    @Tag("create")
    @DisplayName("should prepare key for valid AES key lengths")
    void shouldPrepareKey_forValidAesKeyLengths(int keyBytes) throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        try {
            keySpecField.set(null, null);

            // AES-128/192/256 keys must all be accepted; generateSecretKey() only ever emits
            // 32-byte keys, so the 16- and 24-byte accept branches are otherwise untested.
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR,
                    Base64.getEncoder().encodeToString(new byte[keyBytes]));

            assertThatCode(EncryptionUtils::prepareSecretKeySpec).doesNotThrowAnyException();
            assertThat(keySpecField.get(null)).isNotNull();
        } finally {
            if (originalProp != null) {
                props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
            } else {
                props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
            }
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should prepare key when startup finds a valid existing key")
    void shouldPrepareKey_whenStartupFindsValidExistingKey(@TempDir Path tempDir) throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        Path webappRoot = tempDir.resolve("webapps").resolve("carlos");
        Files.createDirectories(webappRoot);
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(event.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResource("/")).thenReturn(webappRoot.toUri().toURL());

        try {
            System.setProperty("user.home", tempDir.toString());
            // Key-handling test, so give Startup real deployment config to find: packaged defaults no
            // longer count, so without a config file it fails fast (shouldAbortStartup_whenNoConfigFileExists).
            writeDeploymentConfig(tempDir, "carlos");
            String existingKey = EncryptionUtils.generateSecretKey();
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, existingKey);
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            // The existing key is used as-is (not rotated) and the cached spec is prepared.
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(existingKey);
            assertThat(keySpecField.get(null)).isNotNull();

            String encrypted = EncryptionUtils.encrypt("startup-password");
            assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("startup-password");
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should abort startup when configured key is not valid Base64")
    void shouldAbortStartup_whenConfiguredKeyIsInvalid(@TempDir Path tempDir) throws Exception {
        // Bad-Base64 key: decode fails before the length check.
        assertStartupAbortsForInvalidKey("not-base64%%", tempDir);
    }

    @Test
    @Tag("create")
    @DisplayName("should abort startup when configured key has a wrong AES length")
    void shouldAbortStartup_whenConfiguredKeyHasWrongLength(@TempDir Path tempDir) throws Exception {
        // Valid Base64 but a 33-byte key: exercises the length-check abort path, distinct from the
        // bad-Base64 decode path, through the same Startup catch.
        String wrongLengthKey = Base64.getEncoder().encodeToString(new byte[33]);
        assertStartupAbortsForInvalidKey(wrongLengthKey, tempDir);
    }

    @Test
    @Tag("create")
    @DisplayName("should abort startup when key generation fails")
    void shouldAbortStartup_whenKeyGenerationFails(@TempDir Path tempDir) throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        String originalUserHome = System.getProperty("user.home");

        ServletContextEvent event = newStartupEvent(tempDir);

        try (MockedStatic<EncryptionUtils> encryption = mockStatic(EncryptionUtils.class)) {
            System.setProperty("user.home", tempDir.toString());
            // A blank key forces Startup into the generate-and-persist branch.
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "   ");
            keySpecField.set(null, null);
            encryption.when(EncryptionUtils::generateSecretKey)
                    .thenThrow(new NoSuchAlgorithmException("AES unavailable"));

            // Generation failure must abort startup rather than booting with no key. The
            // IllegalStateException is re-wrapped by contextInitialized's outer catch, so it is
            // reachable only as the cause of the propagated RuntimeException.
            assertThatThrownBy(() -> new Startup().contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            restoreUserHome(originalUserHome);
            restoreProperty(props, originalProp);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should generate and persist a new key when startup finds a blank key")
    void shouldGenerateAndPersistKey_whenStartupFindsBlankKey(@TempDir Path tempDir) throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        ServletContextEvent event = newStartupEvent(tempDir);

        try {
            System.setProperty("user.home", tempDir.toString());
            // See the sibling key test: config must exist before Startup reaches key generation.
            writeDeploymentConfig(tempDir, "carlos");
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "   ");
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            // A fresh 32-byte (AES-256) key is generated, persisted, and usable for a round-trip.
            String generated = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
            assertThat(generated).isNotBlank();
            assertThat(Base64.getDecoder().decode(generated)).hasSize(32);
            assertThat(keySpecField.get(null)).isNotNull();

            String encrypted = EncryptionUtils.encrypt("startup-password");
            assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("startup-password");
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should abort startup when no configuration file exists")
    void shouldAbortStartup_whenNoConfigFileExists(@TempDir Path tempDir) throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");

        // CarlosProperties is a process-wide singleton; snapshot and clear it so contextInitialized
        // sees an empty set and both the user-home and WEB-INF lookups fail, reaching the new
        // fail-fast path. Restored in finally so other tests in the JVM are unaffected.
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        ServletContextEvent event = newStartupEvent(tempDir);

        try {
            System.setProperty("user.home", tempDir.toString()); // no carlos.properties here
            props.clear();
            assertThat(props.isEmpty()).isTrue();

            // Missing config = no DB connection and no encryption key: must fail fast rather than
            // boot into a broken state. The IllegalStateException is re-wrapped by the outer catch.
            assertThatThrownBy(() -> new Startup().contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("refusing to start");
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
        }
    }

    @Test
    @Tag("read")
    @DisplayName("should treat missing or key-only DB config as insufficient for the WEB-INF merge")
    void shouldFlagMissingDbConfig_asInsufficientForWebInfMerge() {
        // The fallback is keyed on a usable DB connection, not the size of the properties bag:
        // a key-only stub and non-DB boilerplate both count as "no DB config yet".
        Properties keyOnly = new Properties();
        keyOnly.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "AAAA");
        assertThat(Startup.hasDatabaseConfiguration(keyOnly)).isFalse();

        // Empty set: no DB config.
        assertThat(Startup.hasDatabaseConfiguration(new Properties())).isFalse();

        // Non-DB boilerplate only (classpath /carlos.properties pollution): still no DB config.
        Properties boilerplate = new Properties();
        boilerplate.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "AAAA");
        boilerplate.setProperty("buildVersion", "test-build");
        boilerplate.setProperty("confidentiality_statement.v1", "synthetic notice");
        assertThat(Startup.hasDatabaseConfiguration(boilerplate)).isFalse();

        // A blank db_username is treated as absent, not as usable config.
        Properties blankUsername = new Properties();
        blankUsername.setProperty("db_username", "   ");
        assertThat(Startup.hasDatabaseConfiguration(blankUsername)).isFalse();

        // Real DB config present (with or without a key) must NOT trigger a re-read.
        Properties keyPlusConfig = new Properties();
        keyPlusConfig.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "AAAA");
        keyPlusConfig.setProperty("db_username", "carlos_test_user");
        assertThat(Startup.hasDatabaseConfiguration(keyPlusConfig)).isTrue();

        Properties configNoKey = new Properties();
        configNoKey.setProperty("db_username", "carlos_test_user");
        assertThat(Startup.hasDatabaseConfiguration(configNoKey)).isTrue();
    }

    @Test
    @Tag("create")
    @DisplayName("should preserve WEB-INF config when a second startup sees a key-only user-home stub")
    void shouldPreserveWebInfConfig_whenSecondStartupSeesKeyOnlyUserHomeStub(@TempDir Path tempDir) throws Exception {
        // Regression for issue #2969, the exact two-startup scenario the maintainer asked to cover.
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");

        // CarlosProperties is a process-wide singleton pre-populated from /carlos.properties. Snapshot
        // and clear it so each simulated boot starts from a known-empty set (a fresh JVM). Restored
        // in finally so other tests in the JVM are unaffected.
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        // Context "carlosmerge" -> propName "carlosmerge.properties" -> WEB-INF resource
        // /WEB-INF/carlosmerge.properties (src/test/resources). The unique name keeps this test from
        // colliding with shouldAbortStartup_whenNoConfigFileExists, which drives "carlos" and relies
        // on the WEB-INF read failing.
        ServletContextEvent event = newStartupEvent(tempDir, "carlosmerge");
        Path userHomeStub = tempDir.resolve("carlosmerge.properties");

        try {
            System.setProperty("user.home", tempDir.toString()); // no carlosmerge.properties here yet

            // --- Boot 1: user-home empty; config comes from WEB-INF (no key) -> key generated+persisted.
            props.clear();
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            assertThat(props.getProperty("db_username")).isEqualTo("carlos_test_user");
            String generatedKey = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
            assertThat(generatedKey).isNotBlank();

            // The persisted user-home file is a stub containing ONLY the key - the shadowing hazard.
            assertThat(userHomeStub).exists();
            Properties stub = new Properties();
            try (var in = Files.newInputStream(userHomeStub)) {
                stub.load(in);
            }
            assertThat(stub).containsOnlyKeys(EncryptionUtils.SECRET_KEY_ENV_VAR);

            // --- Boot 2: fresh JVM (clear singleton). user-home now holds the key-only stub.
            props.clear();
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            // The fix: the key-only stub must NOT suppress the WEB-INF config. Before the fix, the
            // p.isEmpty()==false guard skipped WEB-INF and the app booted with a key but no DB config.
            assertThat(props.getProperty("db_username")).isEqualTo("carlos_test_user");
            assertThat(props.getProperty("db_name")).isEqualTo("carlos_test_db");
            // The previously generated key is reused as-is (not rotated).
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(generatedKey);
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should keep the user-home key when the WEB-INF fallback carries a different placeholder key")
    void shouldKeepUserHomeKey_whenWebInfFallbackHasDifferentPlaceholderKey(@TempDir Path tempDir) throws Exception {
        // Regression for the issue #2969 review follow-up: when the user-home stub holds a real
        // generated key and the WEB-INF fallback ALSO defines encryption.util.secret.key (a
        // default/placeholder), a plain Properties.load() merge would overwrite the real key with the
        // placeholder, breaking decryption of everything encrypted since the key was generated. The
        // merge must load DB config from WEB-INF while preserving the user-home key.
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");

        // CarlosProperties is a process-wide singleton; snapshot and clear so this boot starts from a
        // known-empty set (a fresh JVM). Restored in finally so other tests are unaffected.
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        // Context "carlosmergekey" -> /WEB-INF/carlosmergekey.properties (DB config + placeholder key).
        ServletContextEvent event = newStartupEvent(tempDir, "carlosmergekey");

        // The user-home stub already holds ONLY a real, valid generated key: the prior-boot state that
        // #2969 leaves behind. Written directly here so the scenario is deterministic (no reliance on a
        // preceding boot).
        String userHomeKey = EncryptionUtils.generateSecretKey();
        Path userHomeStub = tempDir.resolve("carlosmergekey.properties");
        Properties stub = new Properties();
        stub.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, userHomeKey);
        try (var out = Files.newOutputStream(userHomeStub)) {
            stub.store(out, "issue #2969 regression stub - key only");
        }

        try {
            System.setProperty("user.home", tempDir.toString());
            props.clear();
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            // DB config is loaded from WEB-INF...
            assertThat(props.getProperty("db_username")).isEqualTo("carlos_test_user");
            assertThat(props.getProperty("db_name")).isEqualTo("carlos_test_db");
            // ...but the real user-home key survives the merge - the WEB-INF placeholder must not win.
            // The overwrite hazard comes from Properties.load() of a /WEB-INF/ file that also defines
            // encryption.util.secret.key; the retain-existingKey line above guards it.
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(userHomeKey);

            // The preserved key is valid and prepared, so encryption round-trips under the real key.
            assertThat(keySpecField.get(null)).isNotNull();
            String encrypted = EncryptionUtils.encrypt("merge-guard-password");
            assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("merge-guard-password");
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should load WEB-INF config when singleton carries only non-DB defaults and user-home is a key-only stub")
    void shouldLoadWebInfConfig_whenSingletonHasNonDbDefaultsAndUserHomeIsKeyOnlyStub(@TempDir Path tempDir) throws Exception {
        // The singleton is pre-loaded from classpath /carlos.properties (non-DB boilerplate). With no DB
        // config there, a key-only user-home stub must still trigger the WEB-INF merge - so this test
        // seeds boilerplate instead of clearing the singleton, unlike the sibling tests.
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        // Context "carlosmerge" -> /WEB-INF/carlosmerge.properties (db config, no key).
        ServletContextEvent event = newStartupEvent(tempDir, "carlosmerge");
        Path userHomeStub = tempDir.resolve("carlosmerge.properties");

        try {
            System.setProperty("user.home", tempDir.toString());
            keySpecField.set(null, null);

            // Classpath /carlos.properties contribution: non-DB boilerplate, no DB config.
            props.clear();
            props.setProperty("buildVersion", "test-build");
            props.setProperty("confidentiality_statement.v1", "synthetic notice");

            // user-home holds ONLY a real generated key - the second-boot stub.
            String userHomeKey = EncryptionUtils.generateSecretKey();
            Properties stub = new Properties();
            stub.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, userHomeKey);
            try (var out = Files.newOutputStream(userHomeStub)) {
                stub.store(out, "issue #2969 key-only stub");
            }

            new Startup().contextInitialized(event);

            // The key-only user-home stub must NOT suppress the WEB-INF DB config merge.
            assertThat(props.getProperty("db_username")).isEqualTo("carlos_test_user");
            assertThat(props.getProperty("db_name")).isEqualTo("carlos_test_db");
            // The real user-home key survives (carlosmerge WEB-INF defines no key of its own).
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(userHomeKey);
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    @Test
    @Tag("create")
    @DisplayName("should load WEB-INF config when the packaged defaults already supply a db username")
    void shouldLoadWebInfConfig_whenPackagedDefaultsAlreadySupplyDbUsername(@TempDir Path tempDir) throws Exception {
        // The deployed shape the sibling test hand-seeds away: CarlosProperties pre-loads the packaged
        // /carlos.properties (shipped in the WAR at WEB-INF/classes/), which supplies a db_username. A
        // guard reading the merged singleton therefore sees "configured", skips the /WEB-INF/ fallback,
        // and leaves the deployment on packaged defaults - the #2969 symptom. Loads the real resource
        // rather than a stand-in so the test stays honest if those defaults change.
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalUserHome = System.getProperty("user.home");
        Properties snapshot = new Properties();
        snapshot.putAll(props);

        // Context "carlosmerge" -> /WEB-INF/carlosmerge.properties (db config, no key).
        ServletContextEvent event = newStartupEvent(tempDir, "carlosmerge");
        Path userHomeStub = tempDir.resolve("carlosmerge.properties");

        try {
            System.setProperty("user.home", tempDir.toString());
            keySpecField.set(null, null);

            // The real packaged defaults, exactly as the CarlosProperties constructor loads them.
            props.clear();
            props.readFromFile("/carlos.properties");
            assertThat(props.getProperty("db_username"))
                    .as("packaged /carlos.properties must ship a db_username; without it this test no "
                            + "longer reproduces the #2969 regression")
                    .isNotBlank();
            // Keeps the trailing BASE_DOCUMENT_DIR block from calling mkdirs() outside @TempDir.
            props.remove("BASE_DOCUMENT_DIR");

            // user-home holds ONLY a real generated key - the second-boot stub.
            String userHomeKey = EncryptionUtils.generateSecretKey();
            Properties stub = new Properties();
            stub.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, userHomeKey);
            try (var out = Files.newOutputStream(userHomeStub)) {
                stub.store(out, "issue #2969 key-only stub");
            }

            new Startup().contextInitialized(event);

            // Packaged defaults are not real configuration: the WEB-INF merge must still run and win.
            assertThat(props.getProperty("db_username")).isEqualTo("carlos_test_user");
            assertThat(props.getProperty("db_name")).isEqualTo("carlos_test_db");
            // The real user-home key survives (carlosmerge WEB-INF defines no key of its own).
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(userHomeKey);
        } finally {
            restoreUserHome(originalUserHome);
            props.clear();
            props.putAll(snapshot);
            keySpecField.set(null, originalKeySpec);
        }
    }

    /**
     * Drives {@code Startup.contextInitialized} with an invalid existing key and asserts a fail-fast
     * abort. The abort is raised as an {@link IllegalStateException} but re-wrapped by the method's
     * outer catch, so it surfaces as a {@link RuntimeException} whose cause carries the message and
     * type. Also asserts the stored key is left untouched (no silent rotation) and no spec is set.
     */
    private void assertStartupAbortsForInvalidKey(String invalidKey, Path tempDir) throws Exception {
        Field keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        Object originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        String originalUserHome = System.getProperty("user.home");

        ServletContextEvent event = newStartupEvent(tempDir);

        try {
            System.setProperty("user.home", tempDir.toString());
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, invalidKey);
            keySpecField.set(null, null);

            assertThatThrownBy(() -> new Startup().contextInitialized(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("refusing to start");

            // The stored key is left untouched (no rotation) and no usable spec is established.
            assertThat(props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR)).isEqualTo(invalidKey);
            assertThat(keySpecField.get(null)).isNull();
        } finally {
            restoreUserHome(originalUserHome);
            restoreProperty(props, originalProp);
            keySpecField.set(null, originalKeySpec);
        }
    }

    private static ServletContextEvent newStartupEvent(Path tempDir) throws Exception {
        return newStartupEvent(tempDir, "carlos");
    }

    /**
     * Builds a startup event whose webapp context resolves to {@code contextName}. Startup derives the
     * properties file name ({@code <contextName>.properties}) from the webapp directory name, which in
     * turn selects the {@code /WEB-INF/<contextName>.properties} resource read on the fallback path.
     */
    private static ServletContextEvent newStartupEvent(Path tempDir, String contextName) throws Exception {
        Path webappRoot = tempDir.resolve("webapps").resolve(contextName);
        Files.createDirectories(webappRoot);
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(event.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResource("/")).thenReturn(webappRoot.toUri().toURL());
        return event;
    }

    /**
     * Writes the minimal deployment-supplied config Startup needs to get past the {@code /WEB-INF/}
     * fallback decision. Synthetic values only - no real credentials.
     */
    private static void writeDeploymentConfig(Path tempDir, String contextName) throws IOException {
        Properties config = new Properties();
        config.setProperty("db_username", "carlos_test_user");
        config.setProperty("db_name", "carlos_test_db");
        try (var out = Files.newOutputStream(tempDir.resolve(contextName + ".properties"))) {
            config.store(out, "deployment-supplied config for StartupUnitTest");
        }
    }

    private static void restoreUserHome(String originalUserHome) {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        } else {
            System.clearProperty("user.home");
        }
    }

    private static void restoreProperty(CarlosProperties props, String originalProp) {
        if (originalProp != null) {
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
        } else {
            props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
    }

}
