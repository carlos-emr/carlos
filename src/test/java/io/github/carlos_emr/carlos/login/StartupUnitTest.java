package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.WebappShutdownResources;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                    .isInstanceOf(Exception.class)
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

            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "not-base64%%");
            assertThatCode(() -> EncryptionUtils.prepareSecretKeySpec())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(keySpecField.get(null)).isNull();
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
    @DisplayName("should replace malformed key during startup")
    void shouldReplaceMalformedKey_duringStartup(@TempDir Path tempDir) throws Exception {
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
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, "not-base64%%");
            keySpecField.set(null, null);

            new Startup().contextInitialized(event);

            String replacementKey = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);
            assertThat(replacementKey).isNotBlank();
            assertThat(replacementKey).isNotEqualTo("not-base64%%");
            assertThat(keySpecField.get(null)).isNotNull();

            String encrypted = EncryptionUtils.encrypt("startup-password");
            assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("startup-password");
        } finally {
            if (originalUserHome != null) {
                System.setProperty("user.home", originalUserHome);
            } else {
                System.clearProperty("user.home");
            }
            if (originalProp != null) {
                props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
            } else {
                props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
            }
            keySpecField.set(null, originalKeySpec);
        }
    }

}
