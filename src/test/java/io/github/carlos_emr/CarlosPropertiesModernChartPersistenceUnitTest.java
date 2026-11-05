/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Verifies durable cutover with ordinary Properties, modeling a rollback binary's reader. */
@Tag("unit")
@Tag("create")
class CarlosPropertiesModernChartPersistenceUnitTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"", "AbandonOldChart=false\n", "AbandonOldChart=yes\n",
            "AbandonOldChart=true\nAbandonOldChart=false\n",
            "AbandonOldChart\\u003dignored=false\nAbandonOldChart : off\n"})
    void shouldPersistModernChart_forUnsafeOperatorSettings(String setting) throws Exception {
        String original = "# Preserve operator comment\ndb_name=clinic\n" + setting + "other=value\n";
        Path file = tempDir.resolve("operator.properties");
        Files.writeString(file, original);
        CarlosProperties configuration = configuration(file);

        configuration.ensureModernChartRollbackSafety();

        assertThat(Files.readString(file)).startsWith(original);
        Properties rollback = read(file);
        assertThat(Boolean.parseBoolean(rollback.getProperty("AbandonOldChart"))).isTrue();
        assertThat(rollback.getProperty("db_name")).isEqualTo("clinic");
        assertThat(rollback.getProperty("other")).isEqualTo("value");
        String persisted = Files.readString(file);
        configuration(file).ensureModernChartRollbackSafety();
        assertThat(Files.readString(file)).isEqualTo(persisted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\", "\\\n", "\\\r", "\\\r\n"})
    void shouldPreserveUnrelatedValue_whenFileEndsWithContinuation(String ending) throws Exception {
        Path file = tempDir.resolve("continued.properties");
        Files.writeString(file, "other=preserved" + ending);
        CarlosProperties configuration = configuration(file);

        configuration.ensureModernChartRollbackSafety();

        assertThat(read(file).getProperty("other")).isEqualTo("preserved");
        assertThat(read(file).getProperty("AbandonOldChart")).isEqualTo("true");
    }

    @Test
    void shouldAcceptReadOnlyConfiguration_whenPersistedFlagIsTrue() throws Exception {
        Path file = tempDir.resolve("readonly.properties");
        String original = "# No mutation needed\nAbandonOldChart=TRUE\nother=preserved\n";
        Files.writeString(file, original);
        CarlosProperties configuration = configuration(file);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ));
            configuration.ensureModernChartRollbackSafety();
            assertThat(Files.readString(file)).isEqualTo(original);
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }
    }

    @Test
    void shouldRefuseCutover_whenLoadedSourceHasDisappeared() throws Exception {
        Path file = tempDir.resolve("disappeared.properties");
        Files.writeString(file, "AbandonOldChart=false\nother=preserved\n");
        CarlosProperties configuration = configuration(file);
        Files.delete(file);

        assertThatThrownBy(configuration::ensureModernChartRollbackSafety)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("AbandonOldChart=true")
                .hasMessageContaining("refusing to start");
        assertThat(file).doesNotExist();
    }

    @Test
    void shouldRefuseCutover_whenUnsafeSourceIsReadOnly() throws Exception {
        Path file = tempDir.resolve("readonly-false.properties");
        String original = "AbandonOldChart=false\nother=preserved\n";
        Files.writeString(file, original);
        CarlosProperties configuration = configuration(file);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ));
            // Root can write despite POSIX mode bits; its permission semantics cannot
            // exercise this failure, while the disappeared-source test remains portable.
            assumeFalse(Files.isWritable(file), "Process can bypass read-only mode bits");
            assertThatThrownBy(configuration::ensureModernChartRollbackSafety)
                    .hasMessageContaining(file.toString())
                    .hasMessageContaining("AbandonOldChart=true")
                    .hasMessageContaining("refusing to start");
            assertThat(Files.readString(file)).isEqualTo(original);
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }
    }

    @Test
    void shouldRejectBundledConfiguration_whenMemorySettingIsTrue() throws Exception {
        CarlosProperties configuration = configuration(null);
        configuration.readFromFile("/WEB-INF/startup-configured-encryption-key.properties");
        configuration.setProperty("AbandonOldChart", "true");

        assertThatThrownBy(configuration::ensureModernChartRollbackSafety)
                .hasMessageContaining("durable external properties file")
                .hasMessageContaining("AbandonOldChart=true")
                .hasMessageContaining("refusing to start");
    }

    @Test
    void shouldPersistOneCutover_whenStaleSnapshotsInitializeConcurrently() throws Exception {
        Path file = tempDir.resolve("shared.properties");
        Files.writeString(file, "AbandonOldChart=false\nother=preserved\n");
        CarlosProperties first = configuration(file);
        CarlosProperties second = configuration(file);
        CountDownLatch start = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(3);
        try {
            var firstWrite = workers.submit(() -> {
                start.await();
                first.ensureModernChartRollbackSafety();
                return null;
            });
            var secondWrite = workers.submit(() -> {
                start.await();
                second.ensureModernChartRollbackSafety();
                return null;
            });
            var reads = workers.submit(() -> {
                start.await();
                for (int index = 0; index < 20; index++) {
                    CarlosProperties reader = configuration(file);
                    assertThat(reader.getProperty("other")).isEqualTo("preserved");
                }
                return null;
            });
            start.countDown();
            firstWrite.get(30, TimeUnit.SECONDS);
            secondWrite.get(30, TimeUnit.SECONDS);
            reads.get(30, TimeUnit.SECONDS);
            assertThat(Files.readString(file).lines()
                    .filter(line -> line.equals("AbandonOldChart=true"))).hasSize(1);
            assertThat(read(file).getProperty("AbandonOldChart")).isEqualTo("true");
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CarlosProperties configuration(Path file) throws Exception {
        Constructor<CarlosProperties> constructor = CarlosProperties.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CarlosProperties configuration = constructor.newInstance();
        if (file != null) {
            configuration.readFromFile(file.toString());
        }
        return configuration;
    }

    private Properties read(Path file) throws Exception {
        Properties persisted = new Properties();
        persisted.load(new StringReader(Files.readString(file)));
        return persisted;
    }
}
