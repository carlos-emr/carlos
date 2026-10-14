/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.CarlosProperties;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class BillingOutputLockUnitTest {
    @TempDir Path directory;
    private Object oldHome;
    @BeforeEach void configure() { oldHome = CarlosProperties.getInstance().put("HOME_DIR", directory.toString()); }
    @AfterEach void restore() {
        if (oldHome == null) CarlosProperties.getInstance().remove("HOME_DIR");
        else CarlosProperties.getInstance().put("HOME_DIR", oldHome);
    }

    @Test
    void shouldReleaseLease_whenOperationFails() {
        var expected = new IllegalStateException("render failure");
        assertThatThrownBy(() -> BillingOutputLock.run(() -> { throw expected; })).isSameAs(expected);
        var ran = new AtomicBoolean();
        BillingOutputLock.run(() -> ran.set(true));
        assertThat(ran).isTrue();
    }

    @Test
    void shouldRejectMissingConfiguration_beforeOperation() {
        CarlosProperties.getInstance().remove("HOME_DIR");
        var ran = new AtomicBoolean();
        assertThatThrownBy(() -> BillingOutputLock.run(() -> ran.set(true)))
                .isInstanceOf(BillingFileWriteException.class);
        assertThat(ran).isFalse();
    }

    @Test
    void shouldRejectSymlinkLock_withoutChangingItsTarget() throws Exception {
        Path target = directory.resolve("unrelated.txt");
        Files.writeString(target, "unchanged");
        Files.createSymbolicLink(directory.resolve(".carlos-ohip-disk.lock"), target);
        var ran = new AtomicBoolean();
        assertThatThrownBy(() -> BillingOutputLock.run(() -> ran.set(true)))
                .isInstanceOf(BillingFileWriteException.class);
        assertThat(ran).isFalse();
        assertThat(target).hasContent("unchanged");
    }

    @Test
    void shouldReleaseLocalGuard_whenAnotherChannelAlreadyOwnsLock() throws Exception {
        try (var channel = FileChannel.open(directory.resolve(".carlos-ohip-disk.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThatThrownBy(() -> BillingOutputLock.run(() -> { }))
                    .isInstanceOf(BillingFileWriteException.class).hasMessageContaining("already in progress");
            assertThat(lock.isValid()).isTrue();
        }
        BillingOutputLock.run(() -> { });
    }

    @Test
    void shouldRejectNonDirectoryConfiguration_beforeOperation() throws Exception {
        Path file = directory.resolve("regular.txt");
        Files.writeString(file, "unchanged");
        CarlosProperties.getInstance().setProperty("HOME_DIR", file.toString());
        var ran = new AtomicBoolean();
        assertThatThrownBy(() -> BillingOutputLock.run(() -> ran.set(true)))
                .isInstanceOf(BillingFileWriteException.class);
        assertThat(ran).isFalse();
        assertThat(file).hasContent("unchanged");
    }

    @Test
    void shouldRejectAnotherProcess_thenAcquireAfterItExits() throws Exception {
        Path ready = directory.resolve("child-ready");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), ExternalLock.class.getName(),
                directory.toString()).redirectErrorStream(true).redirectOutput(directory.resolve("child.log").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(ready).exists();
            var ran = new AtomicBoolean();
            assertThatThrownBy(() -> BillingOutputLock.run(() -> ran.set(true)))
                    .isInstanceOf(BillingFileWriteException.class).hasMessageContaining("already in progress");
            assertThat(ran).isFalse();
        } finally {
            child.getOutputStream().close();
            if (!child.waitFor(10, TimeUnit.SECONDS)) child.destroyForcibly().waitFor();
        }
        assertThat(child.exitValue()).isZero();
        BillingOutputLock.run(() -> { });
        assertThat(directory.resolve(".carlos-ohip-disk.lock")).isRegularFile();
    }

    /** Separate JVM proving that exclusion is enforced by the shared filesystem lock. */
    public static final class ExternalLock {
        /** Acquires the lock until the parent closes standard input. */
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            try (var channel = FileChannel.open(directory.resolve(".carlos-ohip-disk.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                if (!lock.isValid()) throw new IllegalStateException("Child lock unavailable");
                Files.createFile(directory.resolve("child-ready"));
                System.in.read();
            }
        }
    }
}
