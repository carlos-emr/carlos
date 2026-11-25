/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * Serializes complete OHIP operations in this JVM and across processes sharing HOME_DIR.
 *
 * @since 2026-09-27
 */
final class BillingOutputLock {
    // Acquire before opening a second descriptor: closing one can release POSIX inode locks.
    private static final StampedLock LOCAL = new StampedLock();
    private static final String LOCK_NAME = ".carlos-ohip-disk.lock";

    private BillingOutputLock() { }

    static void run(Runnable operation) {
        call(() -> {
            operation.run();
            return null;
        });
    }

    static <T> T call(Supplier<T> operation) {
        long stamp = LOCAL.tryWriteLock();
        if (stamp == 0) throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
        try {
            var lockPath = writableOutputFolder().resolve(LOCK_NAME);
            // Keep this inode permanently: deleting it would let another process lock a replacement.
            try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.tryLock()) {
                if (lock == null) throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
                return operation.get();
            }
        } catch (OverlappingFileLockException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, failure);
        } catch (AccessDeniedException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE, failure);
        } catch (IOException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.LOCK_FAILED, failure);
        } finally {
            LOCAL.unlockWrite(stamp);
        }
    }

    /** Opens the authorized file before releasing the reader lock; its response body may then stream independently. */
    static <T extends AutoCloseable> T openDownload(Supplier<T> open) {
        long stamp = LOCAL.tryReadLock();
        if (stamp == 0) throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
        T opened = null;
        try {
            Path path = PathValidationUtils.getRequiredHomeDirectory().toPath().resolve(LOCK_NAME);
            try (var read = SharedRead.acquire(path)) {
                opened = open.get();
                read.checkFirstWriter();
            }
            return opened;
        } catch (IOException | RuntimeException failure) {
            if (opened != null) {
                try { opened.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            }
            if (failure instanceof OverlappingFileLockException) {
                throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, failure);
            }
            if (failure instanceof IOException) {
                throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.LOCK_FAILED, failure);
            }
            throw (RuntimeException) failure;
        } finally {
            LOCAL.unlockRead(stamp);
        }
    }

    /** POSIX releases process locks when another descriptor for the same inode closes. Share one channel per directory. */
    private static final class SharedRead implements AutoCloseable {
        private static final Map<Path, SharedRead> ACTIVE = new HashMap<>();
        private final Path path;
        private final FileLock lock;
        private int readers;

        private SharedRead(Path path, FileLock lock) {
            this.path = path;
            this.lock = lock;
        }

        static SharedRead acquire(Path path) throws IOException {
            synchronized (ACTIVE) {
                SharedRead read = ACTIVE.get(path);
                if (read == null) {
                    read = new SharedRead(path, openSharedLock(path));
                    ACTIVE.put(path, read);
                }
                read.readers++;
                return read;
            }
        }

        private static FileLock openSharedLock(Path path) throws IOException {
            FileChannel channel;
            try { channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException missing) { return null; }
            try {
                var lock = channel.tryLock(0L, Long.MAX_VALUE, true);
                if (lock == null) {
                    throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
                }
                return lock;
            } catch (IOException | RuntimeException failure) {
                try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void checkFirstWriter() throws IOException {
            if (lock != null) return;
            // A historical read-only store may never have generated a disk. Every writer creates this
            // permanent inode before its first database allocation, so seeing it appear invalidates this open.
            try { Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException stillAbsent) { return; }
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
        }

        @Override
        public void close() throws IOException {
            synchronized (ACTIVE) {
                if (--readers == 0) {
                    ACTIVE.remove(path);
                    if (lock != null) {
                        try (var channel = lock.channel()) { lock.release(); }
                    }
                }
            }
        }
    }

    /**
     * Startup creates HOME_DIR only when it derives the value itself, so a deployment that
     * configures the folder but never creates it reached this point as a generic lock failure,
     * after simulation had worked. Checking writability here, before the operation, also covers
     * a lock file that predates a permission change and would otherwise open: the run then
     * allocated disks before the first output write failed.
     */
    private static Path writableOutputFolder() {
        Path folder;
        try {
            folder = PathValidationUtils.getRequiredHomeDirectory().toPath();
        } catch (IOException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE, failure);
        }
        if (!Files.isWritable(folder)) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE, null);
        }
        return folder;
    }
}
