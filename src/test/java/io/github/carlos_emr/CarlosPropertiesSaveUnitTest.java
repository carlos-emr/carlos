/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors */
package io.github.carlos_emr;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins the legacy public append/create API while adding durable serialized writes. */
@Tag("unit")
@Isolated
class CarlosPropertiesSaveUnitTest {
    @TempDir Path directory;

    @Test
    void shouldCreateAndAppend_withoutChangingExistingBytesOrInode() throws Exception {
        CarlosProperties configuration = configuration();
        Path path = directory.resolve("new.properties");
        configuration.saveProperty(path.toString(), "first", "one");
        Object identity = Files.getAttribute(path, "unix:ino");
        byte[] original = Files.readAllBytes(path);
        configuration.saveProperty(path.toString(), "second", "two");
        assertThat(Files.getAttribute(path, "unix:ino")).isEqualTo(identity);
        assertThat(Files.readString(path)).startsWith(new String(original, java.nio.charset.Charset.defaultCharset()));
        assertThat(configuration.getProperty("second")).isEqualTo("two");
    }

    @Test
    void shouldRetainWriteOnlyFileSupport_whenAppendingAnExistingProperty() throws Exception {
        CarlosProperties configuration = configuration();
        Path path = directory.resolve("write-only.properties");
        Files.writeString(path, "# existing\n");
        Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("-w-------"));
        try {
            configuration.saveProperty(path.toString(), "saved", "value");
        } finally {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
        assertThat(Files.readString(path)).contains("# existing\n", "saved=value\n");
    }

    @Test
    void shouldKeepMemoryUnchanged_whenPersistenceFails() throws Exception {
        CarlosProperties configuration = configuration();
        configuration.setProperty("saved", "original");
        assertThatThrownBy(() -> configuration.saveProperty(directory.toString(), "saved", "replacement"))
                .isInstanceOf(java.io.IOException.class);
        assertThat(configuration.getProperty("saved")).isEqualTo("original");
        Path absent = directory.resolve("must-not-create.properties");
        assertThatThrownBy(() -> configuration.saveProperty(absent.toString(), "key", null))
                .isInstanceOf(NullPointerException.class);
        assertThat(absent).doesNotExist();
    }

    @Test
    void shouldPreserveEveryAppend_withConcurrentInstances() throws Exception {
        Path path = directory.resolve("shared.properties");
        Files.writeString(path, "# operator content\n");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < 20; index++) {
                int id = index;
                tasks.add(pool.submit(() -> {
                    try {
                        configuration().saveProperty(path.toString(), "key" + id, "value" + id);
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        Properties persisted = new Properties();
        try (var input = Files.newInputStream(path)) { persisted.load(input); }
        assertThat(persisted).hasSize(20);
        for (int index = 0; index < 20; index++) {
            assertThat(persisted.getProperty("key" + index)).isEqualTo("value" + index);
        }
    }

    private CarlosProperties configuration() throws Exception {
        Constructor<CarlosProperties> constructor = CarlosProperties.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
