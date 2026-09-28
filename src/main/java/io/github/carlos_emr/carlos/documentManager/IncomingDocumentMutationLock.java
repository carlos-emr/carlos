/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fair, per-source exclusion for incoming filing and page mutations in this JVM.
 * Registry references include waiters, so retiring an idle entry cannot create two
 * locks for the same path. Unrelated files never share an operation lock.
 *
 * <p>The thread that actually mutates the file must own the lease until all its
 * writes/rollback finish. Do not acquire outside a cancellable mutation worker:
 * a caller's timeout does not prove that its worker has stopped writing.
 */
public final class IncomingDocumentMutationLock {
    private static final Map<Path, Entry> ENTRIES = new HashMap<>();
    private static final long WAIT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private IncomingDocumentMutationLock() { }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
        private long generation;
    }

    public static Lease acquire(File source, File allowedDirectory) throws IOException {
        return acquire(source, allowedDirectory, true, WAIT_MILLIS);
    }

    /** Reserve an extraction destination before publishing any bytes to its queue name. */
    static Lease reserveNewFile(File destination, File allowedDirectory) throws IOException {
        return acquire(destination, allowedDirectory, false, WAIT_MILLIS);
    }

    static Lease acquire(File source, File allowedDirectory, boolean mustExist, long waitMillis) throws IOException {
        if (waitMillis < 0) throw new IllegalArgumentException("Negative mutation admission deadline");
        File directory = allowedDirectory.getCanonicalFile();
        File validated = PathValidationUtils.validateChildPath(source, directory).getCanonicalFile();
        Path key = validated.toPath();
        Entry entry;
        long reservedGeneration;
        synchronized (ENTRIES) {
            entry = ENTRIES.computeIfAbsent(key, ignored -> new Entry());
            entry.references++;
            reservedGeneration = entry.generation;
        }
        boolean acquired = false;
        boolean handedOff = false;
        try {
            // Timed tryLock honours FIFO fairness; the untimed overload can barge.
            acquired = entry.lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) throw new BoundedPdfTask.BusyException();
            // A terminal removal ends this document's identity even if an upload reuses
            // its queue filename before an already-waiting editor/filer wakes up.
            if (mustExist && reservedGeneration != entry.generation) {
                throw new NoSuchFileException("The incoming document was handled by another session while waiting");
            }
            // Another session may have filed/deleted this file while we waited.
            // Revalidate under the lock before a PDF reader or scratch writer starts.
            File current = PathValidationUtils.validateChildPath(source, directory).getCanonicalFile();
            if (!key.equals(current.toPath())) throw new SecurityException("Incoming document path changed while waiting");
            if (mustExist) {
                if (!Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
                    throw new NoSuchFileException("Incoming document is no longer available in this queue");
                }
                if (!Files.isRegularFile(key, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SecurityException("Incoming document source must be a regular file");
                }
            } else if (Files.exists(key, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException("Incoming extraction destination already exists");
            }
            Lease lease = new Lease(key, current, entry);
            handedOff = true;
            return lease;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Waiting to change the incoming document was interrupted; no change was accepted", interrupted);
        } finally {
            if (!handedOff) {
                if (acquired) entry.lock.unlock();
                releaseReference(key, entry);
            }
        }
    }

    private static void releaseReference(Path key, Entry entry) {
        synchronized (ENTRIES) {
            if (--entry.references == 0) ENTRIES.remove(key, entry);
        }
    }

    /** Tests observe actual queued contention rather than depending on sleeps. */
    static int queuedWaiters(File source) throws IOException {
        Path key = source.getCanonicalFile().toPath();
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            return entry == null ? 0 : entry.lock.getQueueLength();
        }
    }

    static int registeredSources() {
        synchronized (ENTRIES) { return ENTRIES.size(); }
    }

    public static final class Lease implements AutoCloseable {
        private final Path key;
        private final File source;
        private final Entry entry;
        private boolean closed;

        private Lease(Path key, File source, Entry entry) {
            this.key = key;
            this.source = source;
            this.entry = entry;
        }

        public File source() { return source; }

        /**
         * Call after successful filing or whole-document removal, while still holding
         * this lease. Ordinary in-place page edits must not invalidate waiting work.
         * A later reservation may address a newly uploaded document at the same path.
         */
        public void sourceRemoved() {
            if (closed || !entry.lock.isHeldByCurrentThread()) {
                throw new IllegalStateException("Source removal requires the live mutation lease owner");
            }
            synchronized (ENTRIES) { entry.generation++; }
        }

        @Override
        public void close() {
            if (closed) return;
            // A misuse on another thread must not retire the live owner's entry.
            if (!entry.lock.isHeldByCurrentThread()) throw new IllegalStateException("Mutation lease belongs to another thread");
            entry.lock.unlock();
            closed = true;
            releaseReference(key, entry);
        }
    }
}
