/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Publication coordination must survive process boundaries and refused filesystem operations.
 * @since 2026-11-17
 */
@Tag("unit")
@Tag("eform")
@Tag("security")
class EFormImagePublicationLockUnitTest {
    @TempDir
    Path images;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldBlockAnotherJvm_whenPublicationIsProvisional(boolean withToolOptions, @TempDir Path diagnostics) throws Exception {
        Process child = null;
        try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
            Path marker = marker();
            assertThat(marker).isRegularFile().isEmptyFile();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classpath, LockProbe.class.getName(), marker.toString());
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            if (withToolOptions) builder.environment().put("JAVA_TOOL_OPTIONS", "-Dcarlos.eform.lockProbe=FAKE-option-banner");
            Path stderr = diagnostics.resolve("child-stderr.log");
            child = builder.redirectError(stderr.toFile()).start();
            try (var output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
                 var reader = Executors.newSingleThreadExecutor()) {
                try {
                    assertThat(reader.submit(output::readLine).get(10, TimeUnit.SECONDS)).isEqualTo("BLOCKED");
                    lease.close();
                    assertThat(reader.submit(output::readLine).get(10, TimeUnit.SECONDS)).isEqualTo("ACQUIRED");
                    assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(child.exitValue()).isZero();
                    assertThat(output.readLine()).isNull();
                    if (withToolOptions) assertThat(Files.readString(stderr)).contains("FAKE-option-banner");
                } catch (Exception | AssertionError failure) {
                    try {
                        failure.addSuppressed(new AssertionError("Child JVM stderr:\n" + Files.readString(stderr)));
                    } catch (IOException diagnosticFailure) {
                        failure.addSuppressed(diagnosticFailure);
                    }
                    throw failure;
                } finally {
                    // Unblock a pending protocol read before the executor's close waits for it.
                    child.destroyForcibly();
                }
            }
        } finally {
            if (child != null) child.destroyForcibly();
        }
    }

    @Test
    void shouldAllowFurtherPublication_whenLeaseIsClosedTwice() throws Exception {
        var lease = EFormImagePublicationLock.acquire(images.toFile());
        lease.close();
        lease.close();
        assertAvailableToAnotherThread();
        assertThat(marker()).isRegularFile().isEmptyFile();
    }

    @Test
    void shouldPublishAGroupWritableMarker_whenStagingIsShared() throws Exception {
        var staging = createGroupWritableStaging();
        try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
            assertThat(Files.getPosixFilePermissions(marker()))
                    .contains(PosixFilePermission.GROUP_WRITE).doesNotContain(PosixFilePermission.OTHERS_WRITE);
            try (var children = Files.list(staging)) {
                assertThat(children.toList()).containsExactly(marker());
            }
        }
        assertAvailableToAnotherThread();
    }

    @Test
    void shouldPreserveTheInode_whenRepairingAnOlderGroupReadOnlyMarker() throws Exception {
        createGroupWritableStaging();
        Files.createFile(marker(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        Object originalKey = Files.readAttributes(marker(), BasicFileAttributes.class).fileKey();
        try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
            assertThat(Files.readAttributes(marker(), BasicFileAttributes.class).fileKey()).isEqualTo(originalKey);
            assertThat(Files.getPosixFilePermissions(marker())).contains(PosixFilePermission.GROUP_WRITE);
        }
    }

    @Test
    void shouldUseTheExistingInode_whenAnotherPublisherWinsMarkerCreation() throws Exception {
        var staging = createGroupWritableStaging();
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.createLink(eq(marker()), any(Path.class))).thenAnswer(call -> {
                Files.writeString(marker(), "synthetic existing marker");
                throw new FileAlreadyExistsException(marker().toString());
            });
            try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
                assertThat(Files.readString(marker())).isEqualTo("synthetic existing marker");
                try (var children = Files.list(staging)) {
                    assertThat(children.toList()).containsExactly(marker());
                }
            }
        }
    }

    @Test
    void shouldKeepExistingMode_whenMarkerUsesADifferentGroup() throws Exception {
        var staging = createGroupWritableStaging();
        Files.createFile(marker(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        var groupView = mock(PosixFileAttributeView.class);
        var groupAttributes = mock(PosixFileAttributes.class);
        when(groupView.readAttributes()).thenReturn(groupAttributes);
        when(groupAttributes.group()).thenReturn(mock(GroupPrincipal.class));
        when(groupAttributes.permissions()).thenReturn(Files.getPosixFilePermissions(staging));
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.getFileAttributeView(staging, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS))
                    .thenReturn(groupView);
            try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
                assertThat(Files.getPosixFilePermissions(marker())).doesNotContain(PosixFilePermission.GROUP_WRITE);
            }
        }
    }

    @Test
    void shouldKeepAcquiringLocks_whenTheFilesystemHasNoPosixView() throws Exception {
        Path staging = Files.createDirectories(marker().getParent());
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.getFileAttributeView(staging, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS))
                    .thenReturn(null);
            try (var lease = EFormImagePublicationLock.acquire(images.toFile())) {
                assertThat(marker()).isRegularFile();
            }
        }
    }

    @Test
    void shouldRefuseNonregularMarkers_whenStagingIsShared() throws Exception {
        createGroupWritableStaging();
        Files.createDirectory(marker());
        assertThatThrownBy(() -> EFormImagePublicationLock.acquire(images.toFile()))
                .isInstanceOf(IOException.class).hasMessageContaining("must be a regular file");
    }

    @Test
    void shouldRefuseEscapingMarker_whenItIsASymbolicLink(@TempDir Path outside) throws Exception {
        Path retained = Files.writeString(outside.resolve("retained.txt"), "FAKE preexisting bytes");
        Files.createDirectories(marker().getParent());
        Files.createSymbolicLink(marker(), retained);
        var imageDirectory = images.toFile();
        assertThatThrownBy(() -> EFormImagePublicationLock.acquire(imageDirectory))
                .isInstanceOf(SecurityException.class);
        assertThat(Files.readString(retained)).isEqualTo("FAKE preexisting bytes");
        Files.delete(marker());
        assertAvailableToAnotherThread();
    }

    @Test
    void shouldReleaseProcessGuard_whenMarkerCannotBeOpened() throws Exception {
        Files.createDirectories(marker());
        assertThatThrownBy(() -> EFormImagePublicationLock.acquire(images.toFile())).isInstanceOf(IOException.class);
        Files.delete(marker());
        assertAvailableToAnotherThread();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveAcquisitionFailure_whenChannelCleanupAlsoFails(boolean cleanupFails) throws Exception {
        var denied = new IOException("FAKE lock acquisition refused");
        var cleanup = new IOException("FAKE channel close refused");
        FileChannel channel = mock(FileChannel.class);
        when(channel.lock()).thenThrow(denied);
        if (cleanupFails) doThrow(cleanup).when(channel).close();
        try (var channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            channels.when(() -> FileChannel.open(any(Path.class), any(java.nio.file.OpenOption[].class)))
                    .thenReturn(channel);
            assertThatThrownBy(() -> EFormImagePublicationLock.acquire(images.toFile())).isSameAs(denied);
            assertThat(denied.getSuppressed()).containsExactly(cleanupFails ? new Throwable[]{cleanup} : new Throwable[0]);
            verify(channel).close();
        }
        assertAvailableToAnotherThread();
    }

    @Test
    void shouldRetainCommittedOutcome_whenLockReleaseFails() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        FileLock lock = mock(FileLock.class);
        when(channel.lock()).thenReturn(lock);
        doThrow(new IOException("FAKE release refused")).when(lock).release();
        try (var channels = mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            channels.when(() -> FileChannel.open(any(Path.class), any(java.nio.file.OpenOption[].class)))
                    .thenReturn(channel);
            var lease = EFormImagePublicationLock.acquire(images.toFile());
            assertThatCode(lease::close).doesNotThrowAnyException();
            verify(channel).close();
        }
        assertAvailableToAnotherThread();
    }

    private Path marker() {
        return images.resolve("extractFolder").resolve(EFormImagePublicationLock.LOCK_FILE_NAME);
    }

    private Path createGroupWritableStaging() throws IOException {
        assumeTrue(Files.getFileStore(images).supportsFileAttributeView(PosixFileAttributeView.class));
        Path staging = Files.createDirectories(marker().getParent());
        Files.setPosixFilePermissions(staging, PosixFilePermissions.fromString("rwxrwx---"));
        return staging;
    }

    private void assertAvailableToAnotherThread() throws Exception {
        var worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "publication-lock-recovery");
            thread.setDaemon(true);
            return thread;
        });
        try {
            assertThat(worker.submit(() -> {
                try (var _ = EFormImagePublicationLock.acquire(images.toFile())) {
                    return true;
                }
            }).get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            worker.shutdownNow();
        }
    }

    /** Uses a separate JVM and the filesystem protocol without the process-local guard. */
    public static final class LockProbe {
        public static void main(String[] arguments) throws Exception {
            try (var channel = FileChannel.open(Path.of(arguments[0]), StandardOpenOption.WRITE)) {
                try (var unexpected = channel.tryLock()) {
                    if (unexpected != null) {
                        System.out.println("UNEXPECTEDLY_ACQUIRED");
                        return;
                    }
                }
                System.out.println("BLOCKED");
                try (var _ = channel.lock()) {
                    System.out.println("ACQUIRED");
                }
            }
        }
    }
}
