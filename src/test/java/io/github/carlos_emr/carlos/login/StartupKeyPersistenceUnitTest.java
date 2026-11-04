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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real file loads and fresh singletons model two application startups, including key reuse. */
@Isolated
@Tag("unit")
@Tag("create")
class StartupKeyPersistenceUnitTest extends CarlosUnitTestBase {
    private static final String KEY = EncryptionUtils.SECRET_KEY_ENV_VAR;
    private static final String OVERRIDE = "carlos_override_properties";

    @TempDir
    Path tempDir;

    private Field singletonField;
    private Field keySpecField;
    private Object originalProperties;
    private Object originalKeySpec;
    private String originalUserHome;
    private String originalOverride;
    private Path userHome;

    @BeforeEach
    void isolateConfiguration() throws Exception {
        singletonField = CarlosProperties.class.getDeclaredField("carlosProperties");
        singletonField.setAccessible(true);
        originalProperties = singletonField.get(null);
        keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        originalKeySpec = keySpecField.get(null);
        originalUserHome = System.getProperty("user.home");
        originalOverride = System.getProperty(OVERRIDE);
        userHome = Files.createDirectory(tempDir.resolve("home"));
        System.setProperty("user.home", userHome.toString());
        System.clearProperty(OVERRIDE);
    }

    @AfterEach
    void restoreConfiguration() throws Exception {
        Files.setPosixFilePermissions(userHome, PosixFilePermissions.fromString("rwx------"));
        singletonField.set(null, originalProperties);
        keySpecField.set(null, originalKeySpec);
        restoreSystemProperty("user.home", originalUserHome);
        restoreSystemProperty(OVERRIDE, originalOverride);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReusePersistedKey_whenOverrideHasMissingOrBlankKey(String key) throws Exception {
        Path override = writeConfiguration(tempDir.resolve("operator.properties"), key);
        System.setProperty(OVERRIDE, override.toString());
        Files.setPosixFilePermissions(userHome, PosixFilePermissions.fromString("r-x------"));
        CarlosProperties firstBoot = reloadProperties();

        startApplication();

        String generated = firstBoot.getProperty(KEY);
        assertThat(generated).isNotBlank();
        assertThat(Files.readString(override)).contains(KEY + "=" + generated);
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
        String ciphertext = EncryptionUtils.encrypt("two-startup-regression");
        String savedConfiguration = Files.readString(override);

        CarlosProperties secondBoot = reloadProperties();
        startApplication();

        assertThat(secondBoot.getProperty(KEY)).isEqualTo(generated);
        assertThat(secondBoot.getProperty("db_name")).isEqualTo("startup_test");
        assertThat(EncryptionUtils.decrypt(ciphertext)).isEqualTo("two-startup-regression");
        assertThat(Files.readString(override)).isEqualTo(savedConfiguration);
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
    }

    @Test
    void shouldNameLoadedOverride_whenConfigurationCannotBeUpdated() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("readonly.properties"), "");
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties properties = reloadProperties();
        // A directory reliably rejects appends even when CI runs as root, unlike chmod(0400).
        Files.delete(override);
        Files.createDirectory(override);

        assertThatThrownBy(this::startApplication)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(override.toString())
                .hasMessageContaining(KEY)
                .hasMessageContaining("before restarting");
        assertThat(properties.getProperty(KEY)).isEmpty();
        assertThat(keySpecField.get(null)).isNull();
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
    }

    @Test
    void shouldUseLoadedSource_whenOverrideSystemPropertyChangesAfterLoading() throws Exception {
        Path loaded = writeConfiguration(tempDir.resolve("loaded.properties"), "");
        Path neverLoaded = tempDir.resolve("never-loaded.properties");
        System.setProperty(OVERRIDE, loaded.toString());
        reloadProperties();
        System.setProperty(OVERRIDE, neverLoaded.toString());

        startApplication();

        assertThat(Files.readString(loaded)).contains(KEY + "=" + CarlosProperties.getInstance().getProperty(KEY));
        assertThat(neverLoaded).doesNotExist();
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
    }

    @Test
    void shouldPreserveHomeConfiguration_whenItSuppliesTheMissingKey() throws Exception {
        Path homeConfig = writeConfiguration(userHome.resolve("carlos.properties"), "");
        reloadProperties();
        startApplication();
        String key = CarlosProperties.getInstance().getProperty(KEY);
        String ciphertext = EncryptionUtils.encrypt("home-configuration");

        reloadProperties();
        startApplication();

        assertThat(CarlosProperties.getInstance().getProperty(KEY)).isEqualTo(key);
        assertThat(CarlosProperties.getInstance().getProperty("db_name")).isEqualTo("startup_test");
        assertThat(Files.readString(homeConfig)).contains(KEY + "=" + key);
        assertThat(EncryptionUtils.decrypt(ciphertext)).isEqualTo("home-configuration");
    }

    @Test
    void shouldRequestExternalConfiguration_whenOnlyBundledConfigurationWasLoaded() throws Exception {
        CarlosProperties properties = reloadProperties();
        properties.setProperty(KEY, "");

        assertThatThrownBy(this::startApplication)
                .hasMessageContaining("/carlos.properties")
                .hasMessageContaining(KEY)
                .hasMessageContaining("-Dcarlos_override_properties=");
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
        assertThat(properties.getProperty(KEY)).isEmpty();
        assertThat(keySpecField.get(null)).isNull();
    }

    @Test
    void shouldKeepWarResourceUnchanged_whenWebInfConfigurationHasNoKey() throws Exception {
        CarlosProperties properties = reloadProperties();
        String resource = "/WEB-INF/startup-key-persistence.properties";
        properties.clear();
        properties.readFromFile(resource);

        assertThatThrownBy(this::startApplication)
                .hasMessageContaining(resource)
                .hasMessageContaining(KEY)
                .hasMessageContaining("-Dcarlos_override_properties=");
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
        assertThat(properties.getProperty("db_name")).isEqualTo("startup_test");
        assertThat(properties.getProperty(KEY)).isEmpty();
        assertThat(keySpecField.get(null)).isNull();
    }

    private CarlosProperties reloadProperties() throws Exception {
        Constructor<CarlosProperties> constructor = CarlosProperties.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CarlosProperties properties = constructor.newInstance();
        singletonField.set(null, properties);
        keySpecField.set(null, null);
        return properties;
    }

    private Path writeConfiguration(Path path, String key) throws Exception {
        Files.writeString(path, "db_name=startup_test\nBASE_DOCUMENT_DIR=" + tempDir.resolve("documents") + "\n"
                + (key == null ? "" : KEY + "=" + key + "\n"));
        return path;
    }

    private void startApplication() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("webapps/carlos"));
        ServletContext context = mock(ServletContext.class);
        when(context.getResource("/")).thenReturn(root.toUri().toURL());
        new Startup().contextInitialized(new ServletContextEvent(context));
    }

    private static void restoreSystemProperty(String name, String original) {
        if (original == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, original);
        }
    }
}
