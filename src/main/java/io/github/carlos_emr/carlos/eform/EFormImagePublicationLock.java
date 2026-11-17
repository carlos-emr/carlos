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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
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
