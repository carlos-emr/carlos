/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes image publication/rollback and image replacement in an eForm image directory.
 *
 * <p>An import's new public links are provisional until its entire publication pass succeeds.
 * Another import must not adopt them, and an upload must not overwrite their inode, before a
 * failed pass has rolled them back. The process lock avoids overlapping FileLocks within one
 * JVM; the filesystem lock also protects cooperating JVMs sharing the same image directory.
 * Archive extraction and form persistence take place outside this short publication phase.</p>
 *
 * @since 2026-11-17
 */
public final class EFormImagePublicationLock implements AutoCloseable {
    // Persistent, empty coordination metadata in the existing private staging parent. It must
    // not be unlinked after release: another process may already be waiting on this inode.
    static final String LOCK_FILE_NAME = ".publication.lock";
    private static final ReentrantLock PROCESS_LOCK = new ReentrantLock(true);

    private final FileChannel channel;
    private final FileLock lock;
    private boolean closed;

    private EFormImagePublicationLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /** Acquires the publication lease before any public image is inspected or changed. */
    public static EFormImagePublicationLock acquire(File imageDirectory) throws IOException {
        PROCESS_LOCK.lock();
        FileChannel channel = null;
        try {
            File parent = PathValidationUtils.validateGeneratedChildPath("extractFolder", imageDirectory);
            Files.createDirectories(parent.toPath());
            File marker = PathValidationUtils.validateGeneratedChildPath(LOCK_FILE_NAME, parent);
            prepareSharedMarker(parent.toPath(), marker.toPath());
            channel = FileChannel.open(marker.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            return new EFormImagePublicationLock(channel, channel.lock());
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            PROCESS_LOCK.unlock();
            throw failure;
        }
    }

    private static void prepareSharedMarker(Path staging, Path marker) throws IOException {
        var directory = Files.getFileAttributeView(staging, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (directory == null) return;
        var attributes = directory.readAttributes();
        if (!attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)) return;
        try {
            ensureGroupWrite(marker, attributes.group());
        } catch (NoSuchFileException absent) {
            var sharedMode = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw----"));
            Path prepared = Files.createTempFile(staging, ".publication-", ".tmp", sharedMode);
            try {
                // Set the effective mode before the permanent inode becomes visible to other JVMs.
                // A default POSIX ACL can already provide write access. Preserve it when it does.
                prepareNewMarker(prepared, attributes.group());
                try {
                    Files.createLink(marker, prepared);
                } catch (FileAlreadyExistsException anotherPublisherWon) {
                    ensureGroupWrite(marker, attributes.group());
                }
            } finally {
                Files.deleteIfExists(prepared);
            }
        }
    }

    private static void prepareNewMarker(Path marker, GroupPrincipal writers) throws IOException {
        var view = Files.getFileAttributeView(marker, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        var attributes = view.readAttributes();
        if (!writers.equals(attributes.group())) {
            try {
                view.setGroup(writers);
            } catch (FileSystemException denied) {
                // An inherited default ACL can give the other writers access to this group class.
                if (attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)) return;
                throw denied;
            }
        }
        ensureGroupWrite(marker, writers);
    }

    private static void ensureGroupWrite(Path file, GroupPrincipal group) throws IOException {
        var view = Files.getFileAttributeView(file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        var attributes = view.readAttributes();
        if (!attributes.isRegularFile()) throw new IOException("eForm image publication marker must be a regular file");
        var mode = attributes.permissions();
        if (group.equals(attributes.group()) && mode.add(PosixFilePermission.GROUP_WRITE)) {
            view.setPermissions(mode);
        }
    }

    /**
     * Releases coordination without turning an already committed image publication into an
     * apparent import/upload failure. Closing the channel is attempted even if release fails.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        } catch (IOException failure) {
            MiscUtils.getLogger().warn("eForm image publication lease release failed; causeType={}",
                    failure.getClass().getSimpleName());
        } finally {
            PROCESS_LOCK.unlock();
        }
    }
}
