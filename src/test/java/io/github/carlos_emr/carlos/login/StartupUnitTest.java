package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.WebappShutdownResources;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
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
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        String originalUserHome = System.getProperty("user.home");

        Path webappRoot = tempDir.resolve("webapps").resolve("carlos");
        Files.createDirectories(webappRoot);
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(event.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResource("/")).thenReturn(webappRoot.toUri().toURL());

        try {
            System.setProperty("user.home", tempDir.toString());
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
            restoreProperty(props, originalProp);
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
        String originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
        String originalUserHome = System.getProperty("user.home");

        ServletContextEvent event = newStartupEvent(tempDir);

        try {
            System.setProperty("user.home", tempDir.toString());
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
            restoreProperty(props, originalProp);
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
        Path webappRoot = tempDir.resolve("webapps").resolve("carlos");
        Files.createDirectories(webappRoot);
        ServletContextEvent event = mock(ServletContextEvent.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(event.getServletContext()).thenReturn(servletContext);
        when(servletContext.getResource("/")).thenReturn(webappRoot.toUri().toURL());
        return event;
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
