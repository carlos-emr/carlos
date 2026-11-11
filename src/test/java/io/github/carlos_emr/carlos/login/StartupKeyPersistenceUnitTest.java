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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private boolean configurationCaptured;

    /**
     * Captures process-wide state before creating an isolated home and override selection.
     *
     * @throws Exception if reflective state capture or temporary directory creation fails
     */
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
        configurationCaptured = true;
        userHome = Files.createDirectory(tempDir.resolve("home"));
        System.setProperty("user.home", userHome.toString());
        System.clearProperty(OVERRIDE);
    }

    /**
     * Attempts every captured-state restoration even if an earlier restoration fails.
     *
     * @throws Exception if a captured field cannot be restored
     */
    @AfterEach
    void restoreConfiguration() throws Exception {
        if (!configurationCaptured) {
            return;
        }
        try {
            singletonField.set(null, originalProperties);
        } finally {
            try {
                keySpecField.set(null, originalKeySpec);
            } finally {
                try {
                    restoreSystemProperty("user.home", originalUserHome);
                } finally {
                    restoreSystemProperty(OVERRIDE, originalOverride);
                }
            }
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReusePersistedKey_whenOverrideHasMissingOrBlankKey(String key) throws Exception {
        Path override = writeConfiguration(tempDir.resolve("operator.properties"), key);
        System.setProperty(OVERRIDE, override.toString());
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

    @ParameterizedTest
    @ValueSource(strings = {"\\", "\\\n", "\\\r", "\\\r\n"})
    void shouldReusePersistedKey_whenConfigurationEndsWithContinuation(String ending) throws Exception {
        Path override = writeConfiguration(tempDir.resolve("continued.properties"), "");
        Files.writeString(override, Files.readString(override) + "continued=preserve" + ending);
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties firstBoot = reloadProperties();
        assertThat(firstBoot.getProperty("continued")).isEqualTo("preserve");
        startApplication();
        String key = firstBoot.getProperty(KEY);
        String ciphertext = EncryptionUtils.encrypt("continued-configuration");
        String savedConfiguration = Files.readString(override);

        CarlosProperties secondBoot = reloadProperties();
        startApplication();

        assertThat(secondBoot.getProperty(KEY)).isEqualTo(key);
        assertThat(secondBoot.getProperty("continued")).isEqualTo("preserve");
        assertThat(EncryptionUtils.decrypt(ciphertext)).isEqualTo("continued-configuration");
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRequestExternalConfiguration_whenOnlyBundledConfigurationWasLoaded(String key) throws Exception {
        CarlosProperties properties = reloadProperties();
        properties.setProperty("db_name", "startup_test");
        if (key == null) {
            properties.remove(KEY);
        } else {
            properties.setProperty(KEY, key);
        }

        assertThatThrownBy(this::startApplication)
                .hasMessageContaining("/carlos.properties")
                .hasMessageContaining(KEY)
                .hasMessageContaining("-Dcarlos_override_properties=")
                .hasMessageContaining("Restore the original key")
                .hasMessageContaining("https://github.com/carlos-emr/carlos/blob/release/2026.08/docs/startup-encryption-key.md");
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
        assertThat(properties.getProperty(KEY)).isEqualTo(key);
        assertThat(keySpecField.get(null)).isNull();
    }

    @Test
    void shouldRefuseRollbackUnsafeStartup_whenOnlyClasspathConfigurationWasLoaded() throws Exception {
        String resource = "/WEB-INF/startup-configured-encryption-key.properties";
        byte[] originalResource;
        try (var input = getClass().getResourceAsStream(resource)) {
            originalResource = input.readAllBytes();
        }
        CarlosProperties properties = reloadProperties();
        properties.clear();
        properties.readFromFile(resource);
        String configuredKey = properties.getProperty(KEY);

        assertThatThrownBy(this::startApplication)
                .hasMessageContaining("AbandonOldChart=true")
                .hasMessageContaining("durable external properties file")
                .hasMessageContaining("-Dcarlos_override_properties=")
                .hasMessageContaining("refusing to start");

        assertThat(properties.getProperty(KEY)).isEqualTo(configuredKey);
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input.readAllBytes()).isEqualTo(originalResource);
        }
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

    @Test
    void shouldAbortStartup_whenKeyGenerationFails() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("operator.properties"), "");
        String original = Files.readString(override);
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties properties = reloadProperties();

        try (MockedStatic<EncryptionUtils> encryption = mockStatic(EncryptionUtils.class)) {
            encryption.when(EncryptionUtils::generateSecretKey)
                    .thenThrow(new NoSuchAlgorithmException("AES unavailable"));
            assertThatThrownBy(this::startApplication)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES unavailable");
            encryption.verify(EncryptionUtils::generateSecretKey);
        }

        assertThat(Files.readString(override)).isEqualTo(original);
        assertThat(properties.getProperty(KEY)).isEmpty();
        assertThat(keySpecField.get(null)).isNull();
        assertThat(userHome.resolve("carlos.properties")).doesNotExist();
    }

    /**
     * Starts two blank-key snapshots together and verifies one persisted initialization.
     *
     * @throws Exception if configuration setup or concurrent initialization fails
     */
    @Test
    void shouldReuseOneKey_whenInstancesInitializeConcurrently() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("shared.properties"), "");
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties first = reloadProperties();
        CarlosProperties second = createProperties();
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var firstKey = workers.submit(() -> {
                start.await();
                return first.getOrCreateEncryptionKey();
            });
            var secondKey = workers.submit(() -> {
                start.await();
                return second.getOrCreateEncryptionKey();
            });
            start.countDown();
            assertThat(firstKey.get(30, TimeUnit.SECONDS)).isEqualTo(secondKey.get(30, TimeUnit.SECONDS));
            assertThat(Files.readString(override).lines()
                    .filter(line -> line.startsWith(KEY + "=") && !line.substring(KEY.length() + 1).isBlank()))
                    .hasSize(1);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Coordinates two JVMs after both load a blank key, then decrypts both outputs with
     * the single persisted key. Always terminates child processes after the assertions.
     *
     * @throws Exception if process coordination, startup or ciphertext verification fails
     */
    @Test
    void shouldReuseOneKey_whenProcessesStartConcurrently() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("shared.properties"), "");
        Path root = Files.createDirectories(tempDir.resolve("webapps/carlos"));
        Path start = tempDir.resolve("start");
        List<Process> workers = new ArrayList<>();
        List<Path> homes = new ArrayList<>();
        try (var readiness = FileSystems.getDefault().newWatchService()) {
            for (int index = 0; index < 2; index++) {
                Path home = Files.createDirectory(tempDir.resolve("process-" + index));
                homes.add(home);
                home.register(readiness, StandardWatchEventKinds.ENTRY_CREATE);
                String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
                workers.add(new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Xmx128m", "-Djava.io.tmpdir=" + home, "-cp", classpath,
                        StartupKeyPersistenceProcessFixture.class.getName(),
                        override.toString(), home.toString(), root.toString(),
                        home.resolve("ready").toString(), start.toString(), home.resolve("ciphertext").toString())
                        .redirectErrorStream(true).redirectOutput(home.resolve("startup.log").toFile()).start());
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (homes.stream().anyMatch(home -> !Files.exists(home.resolve("ready")))
                    && System.nanoTime() < deadline && workers.stream().allMatch(Process::isAlive)) {
                var change = readiness.poll(10, TimeUnit.MILLISECONDS);
                if (change != null) {
                    change.pollEvents();
                    change.reset();
                }
            }
            assertThat(homes).allMatch(home -> Files.exists(home.resolve("ready")));
            Files.createFile(start);
            for (int index = 0; index < workers.size(); index++) {
                Process worker = workers.get(index);
                assertThat(worker.waitFor(45, TimeUnit.SECONDS)).isTrue();
                assertThat(worker.exitValue()).withFailMessage(Files.readString(homes.get(index).resolve("startup.log")))
                        .isZero();
            }
            System.setProperty(OVERRIDE, override.toString());
            reloadProperties();
            startApplication();
            for (Path home : homes) {
                assertThat(EncryptionUtils.decrypt(Files.readString(home.resolve("ciphertext"))))
                        .isEqualTo("concurrent-startup-credential");
                assertThat(home.resolve("carlos.properties")).doesNotExist();
            }
            assertThat(Files.readString(override).lines()
                    .filter(line -> line.startsWith(KEY + "=") && !line.substring(KEY.length() + 1).isBlank()))
                    .hasSize(1);
            // Both JVMs also pass the durable cutover guard against this shared file.
            assertThat(Files.readString(override).lines()
                    .filter(line -> line.equals("AbandonOldChart=true"))).hasSize(1);
        } finally {
            workers.forEach(Process::destroyForcibly);
            for (Process worker : workers) {
                assertThat(worker.waitFor(10, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void shouldRefuseStartup_whenModernChartCutoverCannotBePersisted() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("cutover.properties"),
                "AAAAAAAAAAAAAAAAAAAAAA==");
        System.setProperty(OVERRIDE, override.toString());
        reloadProperties();
        Files.delete(override);
        Files.createDirectory(override);

        assertThatThrownBy(this::startApplication)
                .hasMessageContaining("AbandonOldChart=true")
                .hasMessageContaining(override.toString())
                .hasMessageContaining("refusing to start");
    }

    /**
     * Installs a fresh configuration singleton and clears the prepared encryption key.
     *
     * @return CarlosProperties the freshly loaded active configuration
     * @throws Exception if configuration construction or singleton replacement fails
     */
    private CarlosProperties reloadProperties() throws Exception {
        CarlosProperties properties = createProperties();
        singletonField.set(null, properties);
        keySpecField.set(null, null);
        return properties;
    }

    /**
     * Constructs an independent configuration snapshot without replacing the singleton.
     *
     * @return CarlosProperties the newly loaded configuration
     * @throws Exception if reflective construction fails
     */
    private CarlosProperties createProperties() throws Exception {
        Constructor<CarlosProperties> constructor = CarlosProperties.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * Writes fixture database and document settings with an optional encryption entry.
     *
     * @param path Path the destination properties file
     * @param key String the key value, or null to omit the property entirely
     * @return Path the written configuration file
     * @throws Exception if the fixture file cannot be written
     */
    private Path writeConfiguration(Path path, String key) throws Exception {
        Files.writeString(path, "db_name=startup_test\nBASE_DOCUMENT_DIR=" + tempDir.resolve("documents") + "\n"
                + (key == null ? "" : KEY + "=" + key + "\n"));
        return path;
    }

    @Test
    void shouldRefuseSelectedOverride_whenItCannotBeRead() throws Exception {
        Path missing = tempDir.resolve("explicit-missing.properties");
        System.setProperty(OVERRIDE, missing.toString());
        assertThatThrownBy(this::createProperties).cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missing.toString(), "configured override", "refusing to start");
        assertThat(missing).doesNotExist();
    }

    @Test
    void shouldReportActualKeyDestination_whenLegacyHomeShadowsOverride() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("selected.properties"), "");
        Path home = writeConfiguration(userHome.resolve("carlos.properties"), "");
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties configuration = reloadProperties();
        try (LogCapture logs = LogCapture.forLogger(Startup.class)) {
            startApplication();
            assertThat(logs.messages().stream().filter(message -> message.contains("Legacy home configuration")))
                    .singleElement().asString().contains(home.toString(), override.toString());
            assertThat(logs.messages().stream().filter(message -> message.contains("Secret Key initialized")))
                    .singleElement().asString().contains(home.toString(), "backups")
                    .doesNotContain(configuration.getProperty(KEY));
        }
        assertThat(configuration.getConfigurationSource()).isEqualTo(home.toString());
        assertThat(Files.readString(home)).contains(KEY + "=" + configuration.getProperty(KEY));
        assertThat(Files.readString(override)).doesNotContain(KEY + "=" + configuration.getProperty(KEY));
    }

    @Test
    void shouldKeepExplicitConfigurationAndLogFailures_whenServletMetadataAndAttributesFail() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("selected.properties"), "");
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties configuration = reloadProperties();
        ServletContext context = mock(ServletContext.class);
        when(context.getResource("/")).thenThrow(new java.net.MalformedURLException("Synthetic metadata failure"));
        org.mockito.Mockito.doThrow(new IllegalStateException("Synthetic attribute failure"))
                .when(context).setAttribute(org.mockito.ArgumentMatchers.eq("CaseMgmtUsers"), org.mockito.ArgumentMatchers.any());
        try (LogCapture logs = LogCapture.forLogger(Startup.class)) {
            new Startup().contextInitialized(new ServletContextEvent(context));
            assertThat(logs.messages()).anyMatch(message -> message.startsWith("Error: "))
                    .anyMatch(message -> message.startsWith("Application properties could not be loaded:"));
        }
        assertThat(configuration.getConfigurationSource()).isEqualTo(override.toString());
        assertThat(configuration.getProperty(KEY)).isNotBlank();
        assertThat(Files.readString(override)).contains(KEY + "=" + configuration.getProperty(KEY));
        assertThat(keySpecField.get(null)).isNotNull();
        assertThat(userHome.resolve(".properties")).doesNotExist();
    }

    @Test
    void shouldReuseKeyWithoutShadowWarning_whenHomeAndOverrideNameTheSameFile() throws Exception {
        Path home = writeConfiguration(userHome.resolve("carlos.properties"), "");
        System.setProperty(OVERRIDE, home.toString());
        CarlosProperties configuration = reloadProperties();
        try (LogCapture logs = LogCapture.forLogger(Startup.class)) {
            startApplication();
            assertThat(logs.messages()).noneMatch(message -> message.contains("Legacy home configuration"));
        }
        String saved = Files.readString(home);
        String key = configuration.getProperty(KEY);
        String encrypted = EncryptionUtils.encrypt("same-file-startup");
        reloadProperties();
        startApplication();
        assertThat(Files.readString(home)).isEqualTo(saved);
        assertThat(CarlosProperties.getInstance().getProperty(KEY)).isEqualTo(key);
        assertThat(EncryptionUtils.decrypt(encrypted)).isEqualTo("same-file-startup");
    }

    @Test
    void shouldKeepPriorSettings_whenNewSourceIsMalformed() throws Exception {
        Path override = writeConfiguration(tempDir.resolve("selected.properties"), "");
        System.setProperty(OVERRIDE, override.toString());
        CarlosProperties configuration = reloadProperties();
        Path malformed = tempDir.resolve("malformed.properties");
        Files.writeString(malformed, "db_name=must-not-apply\nbad=\\uZZZZ\n");
        assertThatThrownBy(() -> configuration.readFromFile(malformed.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(configuration.getProperty("db_name")).isEqualTo("startup_test");
        assertThat(configuration.getConfigurationSource()).isEqualTo(override.toString());
    }

    /**
     * Runs the real startup listener with a minimal servlet context and temporary webapp.
     *
     * @throws Exception if webapp setup or application startup fails
     */
    private void startApplication() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("webapps/carlos"));
        ServletContext context = mock(ServletContext.class);
        when(context.getResource("/")).thenReturn(root.toUri().toURL());
        new Startup().contextInitialized(new ServletContextEvent(context));
    }

    /**
     * Restores the original value or absence of a process-wide property.
     *
     * @param name String the system property name
     * @param original String the previous value, or null when the property was absent
     */
    private static void restoreSystemProperty(String name, String original) {
        if (original == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, original);
        }
    }
}
