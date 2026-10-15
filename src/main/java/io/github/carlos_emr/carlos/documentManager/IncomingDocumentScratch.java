/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns a private, same-filesystem PDF rewrite scratch file. An OS lock protects
 * live work, including work in another JVM. A crashed process releases its lock;
 * queue access or the next rewrite then reaps at most 100 abandoned files older
 * than 24 hours. Published PDFs and symlinks are never cleanup candidates.
 */
final class IncomingDocumentScratch implements AutoCloseable {
    private static final long MAX_AGE_MILLIS = Duration.ofDays(1).toMillis();
    private static final int CLEANUP_LIMIT = 100;
    private static final Set<Path> ACTIVE = ConcurrentHashMap.newKeySet();
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;

    private IncomingDocumentScratch(Path path, FileChannel channel, FileLock lock) {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
        ACTIVE.add(path);
    }

    static IncomingDocumentScratch create(File directory) throws IOException {
        File validated = validateDirectory(directory);
        cleanup(validated);
        Path path = Files.createTempFile(validated.toPath(), ".carlos-", ".tmp");
        FileChannel channel = null;
        try {
            channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            return new IncomingDocumentScratch(path, channel, channel.lock());
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { Files.deleteIfExists(path); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    File file() {
        return path.toFile();
    }

    OutputStream output() {
        // Keep one descriptor for the lifetime of the rewrite. On POSIX systems,
        // closing a second descriptor for the same inode can release its locks.
        // PDF writers may close their output; only this owner closes the channel.
        return new FilterOutputStream(Channels.newOutputStream(channel)) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException { flush(); }
        };
    }

    static void cleanup(File directory) {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS;
        int attempted = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(validateDirectory(directory).toPath(), ".carlos-*.tmp")) {
            for (Path candidate : entries) {
                // createTempFile generates a numeric suffix. Do not treat arbitrary hidden
                // files, directories or links as artifacts owned by this component.
                if (!candidate.getFileName().toString().matches("\\.carlos-[0-9]+\\.tmp")
                        || ACTIVE.contains(candidate)
                        || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.getLastModifiedTime(candidate, LinkOption.NOFOLLOW_LINKS).toMillis() >= cutoff) {
                    continue;
                }
                if (++attempted > CLEANUP_LIMIT) { break; }
                try (FileChannel channel = FileChannel.open(candidate, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                     FileLock lock = channel.tryLock(0L, Long.MAX_VALUE, true)) {
                    if (lock != null) { Files.deleteIfExists(candidate); }
                } catch (OverlappingFileLockException activeInThisJvm) {
                    // A long-running rewrite in this JVM still owns this file.
                } catch (IOException failure) {
                    MiscUtils.getLogger().warn("Could not remove an abandoned incoming-document scratch file");
                }
            }
        } catch (IOException | SecurityException failure) {
            MiscUtils.getLogger().warn("Could not scan incoming-document scratch files for cleanup");
        }
    }

    private static File validateDirectory(File directory) {
        File root = PathValidationUtils.validateConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR"), "incoming document root");
        return PathValidationUtils.validateExistingPath(directory, root);
    }

    @Override
    public void close() {
        // Cleanup cannot change the reported outcome of an already-published replacement.
        try { lock.release(); } catch (IOException failure) {
            MiscUtils.getLogger().warn("Could not release an incoming-document scratch lock");
        }
        try { channel.close(); } catch (IOException failure) {
            MiscUtils.getLogger().warn("Could not close an incoming-document scratch channel");
        }
        try { Files.deleteIfExists(path); } catch (IOException failure) {
            MiscUtils.getLogger().warn("Could not remove an incoming-document scratch file");
        } finally {
            ACTIVE.remove(path);
        }
    }
}
