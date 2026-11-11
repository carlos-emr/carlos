/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

/**
 * JDK-only preparation for the packaged service, before Tomcat accepts a start.
 * The separately installed launcher contains only this class, never application
 * libraries or Spring initialization. The root invocation touches only the
 * managed configuration; the second invocation runs with the service's identity.
 */
public final class PackagedConfigurationPreflight {
    private static final String MODERN = "AbandonOldChart";
    private static final String KEY = "encryption.util.secret.key";
    private static final byte[] ENTRY = ("\n\n# Mandatory modern-chart cutover; retain this on rollback (#4793).\n"
            + "AbandonOldChart=true\n").getBytes(StandardCharsets.ISO_8859_1);
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);

    private PackagedConfigurationPreflight() {
    }

    /**
     * Reconciles the root-owned packaged file, or checks the effective files as
     * the service user. No mode creates configuration or generates a new key.
     */
    public static void main(String[] args) {
        if (args.length != 2 || !(args[0].equals("reconcile") || args[0].equals("verify"))) {
            System.err.println("usage: PackagedConfigurationPreflight reconcile|verify /path/to/carlos.properties");
            System.exit(2);
        }
        try {
            Path path = Path.of(args[1]);
            if (args[0].equals("reconcile")) {
                reconcile(path, true);
            } else {
                verify(path, Path.of(System.getProperty("user.home"), "carlos.properties"));
            }
        } catch (IOException | RuntimeException failure) {
            // Properties parse errors and file paths can contain untrusted text.
            // Only our own contextual message and the exception type are emitted.
            System.err.println("carlos-emr: configuration preflight refused ("
                    + failure.getClass().getSimpleName() + "): " + safe(failure.getMessage()));
            System.err.println("carlos-emr: the application was not started. Restore the configuration/key "
                    + "or run sudo dpkg-reconfigure carlos-emr; see docs/modern-chart-cutover.md.");
            System.exit(1);
        }
    }

    static void reconcile(Path configured, boolean requireRootOwnership) throws IOException {
        long deadline = System.nanoTime() + LOCK_TIMEOUT.toNanos();
        while (true) {
            Path path = configured.toRealPath();
            BasicFileAttributes identity = identity(path);
            if (requireRootOwnership) {
                requireRootControlled(configured, path);
            }
            // A persisted true needs only read access, including on read-only mounts.
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
                 FileLock lock = acquire(channel, true, deadline)) {
                Properties stored = read(channel);
                if (Boolean.parseBoolean(stored.getProperty(MODERN)) && sameFile(configured, identity)) {
                    return;
                }
            }
            if (!sameFile(configured, identity)) {
                retry(deadline);
                continue;
            }
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock lock = acquire(channel, false, deadline)) {
                if (!sameFile(configured, identity)) {
                    retry(deadline);
                    continue;
                }
                Properties stored = read(channel);
                if (!Boolean.parseBoolean(stored.getProperty(MODERN))) {
                    if (stored.containsKey(MODERN)) {
                        System.err.println("carlos-emr: overriding the stored AbandonOldChart setting in "
                                + safe(path.toString()) + "; the modern chart is mandatory. Retain true on rollback.");
                    }
                    // Append under the same POSIX record lock used by Java startup.
                    // Never rename: an existing key writer may be waiting on this inode.
                    channel.position(channel.size());
                    ByteBuffer entry = ByteBuffer.wrap(ENTRY);
                    while (entry.hasRemaining()) {
                        channel.write(entry);
                    }
                    channel.force(true);
                }
                if (sameFile(configured, identity)) {
                    return;
                }
            }
            // The unchanged CLI can replace the file. Recheck the pathname;
            // accepting a successful write to an unlinked inode would be unsafe.
            retry(deadline);
        }
    }

    static void verify(Path managed, Path home) throws IOException {
        Properties effective = readFile(managed);
        Path last = managed;
        if (!Files.notExists(home) && !Files.isSameFile(managed, home)) {
            System.err.println("carlos-emr: legacy home configuration " + safe(home.toString())
                    + " loads after " + safe(managed.toString())
                    + "; preserve its existing encryption key and include the effective file in backups.");
            effective.putAll(readFile(home));
            last = home;
        }
        // The legacy file, when present, is prepared only with the service user's
        // permissions, exactly as application startup did. Root never modifies it.
        reconcile(last, false);
        String key = effective.getProperty(KEY);
        if (key == null || key.isBlank()) {
            throw new IOException("No persisted encryption key in the effective configuration " + safe(last.toString())
                    + "; restore the original key before restarting an existing installation");
        }
        key = key.trim(); // Match EncryptionUtils.prepareSecretKeySpec's legacy whitespace handling.
        try {
            int length = Base64.getDecoder().decode(key).length;
            if (length != 16 && length != 24 && length != 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid persisted encryption key in the effective configuration "
                    + safe(last.toString()) + "; restore the original key; no replacement was generated");
        }
    }

    private static Properties readFile(Path path) throws IOException {
        identity(path);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
             FileLock lock = acquire(channel, true, System.nanoTime() + LOCK_TIMEOUT.toNanos())) {
            return read(channel);
        }
    }

    private static Properties read(FileChannel channel) throws IOException {
        Properties properties = new Properties();
        channel.position(0);
        try {
            // Use Java's actual ISO-8859-1/escape/continuation/duplicate semantics.
            properties.load(Channels.newInputStream(channel));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Malformed Java properties; configuration was not rewritten");
        }
        return properties;
    }

    private static BasicFileAttributes identity(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            throw new IOException("Configuration is not a regular file: " + safe(path.toString()));
        }
        return attributes;
    }

    private static boolean sameFile(Path path, BasicFileAttributes before) throws IOException {
        if (before.fileKey() == null) {
            throw new IOException("Configuration filesystem does not provide stable file identity; use a local POSIX filesystem");
        }
        return Objects.equals(before.fileKey(), identity(path).fileKey());
    }

    private static void requireRootControlled(Path configured, Path resolved) throws IOException {
        // The production wrapper selects a fixed /etc path. Check its link's
        // parents as well as the resolved target, without trusting an environment
        // variable or following a service-user-controlled directory as root.
        for (Path initial : new Path[] {configured.toAbsolutePath(), resolved}) {
            for (Path path = initial; path != null; path = path.getParent()) {
                int uid = ((Number) Files.getAttribute(path, "unix:uid", LinkOption.NOFOLLOW_LINKS)).intValue();
                int mode = ((Number) Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue();
                if (uid != 0 || (!Files.isSymbolicLink(path) && (mode & 0022) != 0)) {
                    throw new IOException("Packaged configuration path is not root-controlled: " + safe(path.toString()));
                }
            }
        }
    }

    private static FileLock acquire(FileChannel channel, boolean shared, long deadline) throws IOException {
        while (true) {
            try {
                FileLock lock = channel.tryLock(0, Long.MAX_VALUE, shared);
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException busy) {
                // Another thread in this JVM owns the same record lock.
            }
            retry(deadline);
        }
    }

    private static void retry(long deadline) throws IOException {
        if (System.nanoTime() >= deadline) {
            throw new IOException("Configuration stayed busy or was replaced for 10 seconds; retry after the writer finishes");
        }
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Configuration preparation was interrupted", interrupted);
        }
    }

    private static String safe(String value) {
        return value == null ? "no further detail" : value.replaceAll("[\\p{Cntrl}]", "_");
    }
}
