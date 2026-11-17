/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Semaphore;

/**
 * Serializes complete OHIP operations in this JVM and across processes sharing HOME_DIR.
 *
 * @since 2026-09-27
 */
final class BillingOutputLock {
    // Acquire before opening a second descriptor: closing one can release POSIX inode locks.
    private static final Semaphore LOCAL = new Semaphore(1);

    private BillingOutputLock() { }

    static void run(Runnable operation) {
        if (!LOCAL.tryAcquire()) throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
        try {
            var lockPath = writableOutputFolder().resolve(".carlos-ohip-disk.lock");
            // Keep this inode permanently: deleting it would let another process lock a replacement.
            try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.tryLock()) {
                if (lock == null) throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, null);
                operation.run();
            }
        } catch (OverlappingFileLockException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.BUSY, failure);
        } catch (AccessDeniedException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE, failure);
        } catch (IOException failure) {
            throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.LOCK_FAILED, failure);
        } finally {
            LOCAL.release();
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
