/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Semaphore;

/** Serializes complete OHIP operations in this JVM and across processes sharing HOME_DIR. */
final class BillingOutputLock {
    // Acquire before opening a second descriptor: closing one can release POSIX inode locks.
    private static final Semaphore LOCAL = new Semaphore(1);
    private static final String BUSY = "An OHIP disk operation is already in progress; wait for it to finish before retrying";

    private BillingOutputLock() { }

    static void run(Runnable operation) {
        if (!LOCAL.tryAcquire()) throw new BillingFileWriteException(BUSY);
        try {
            var lockPath = PathValidationUtils.getRequiredHomeDirectory().toPath().resolve(".carlos-ohip-disk.lock");
            // Keep this inode permanently: deleting it would let another process lock a replacement.
            try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 var lock = channel.tryLock()) {
                if (lock == null) throw new BillingFileWriteException(BUSY);
                operation.run();
            }
        } catch (OverlappingFileLockException failure) {
            throw new BillingFileWriteException(BUSY, failure);
        } catch (IOException failure) {
            throw new BillingFileWriteException("OHIP output lock failed; reconcile any completed output before retrying", failure);
        } finally {
            LOCAL.release();
        }
    }
}
