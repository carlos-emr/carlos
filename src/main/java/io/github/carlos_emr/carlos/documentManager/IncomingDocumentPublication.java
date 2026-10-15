/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publishes complete incoming bytes without replacing another document, across filesystems. */
public final class IncomingDocumentPublication {
    private IncomingDocumentPublication() { }

    /**
     * Both paths must already be containment-validated by the caller. The caller must hold
     * the source mutation lease until this operation finishes. A private staging directory
     * on the destination filesystem makes the final hard-link publication atomic and
     * no-clobber, including when the source is on another filesystem. Unsupported hard
     * links fail before source deletion. A failed source deletion removes only the published
     * link still belonging to our staging inode; it never removes a replacement document.
     */
    public static void move(File source, File destination) throws IOException {
        move(source.toPath(), destination.toPath(), new FileOperations());
    }

    // The caller validates both paths against configured roots; private children are generated
    // by createTempDirectory under the validated destination parent, never from request input.
    @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN", "PATH_TRAVERSAL_OUT"},
            justification = "Callers containment-validate source and destination; staging is privately generated under the approved destination parent")
    static void move(Path source, Path destination, FileOperations operations) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Incoming publication source must be a regular file");
        }
        // Avoid copying a large PDF once for each already-used suffix. This is only
        // an optimization: createLink below remains the atomic collision authority.
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException("Incoming publication destination already exists");
        }
        Path directory = Files.createTempDirectory(destination.toAbsolutePath().getParent(), ".carlos-publication-");
        Path staged = directory.resolve("payload");
        boolean published = false;
        boolean committed = false;
        try {
            checkInterrupted();
            operations.copy(source, staged);
            if (!Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Incoming publication staging must be a regular file");
            }
            checkInterrupted();
            try {
                operations.link(destination, staged);
            } catch (UnsupportedOperationException unsupported) {
                throw new IOException("Document storage does not support safe atomic publication", unsupported);
            }
            published = true;
            checkInterrupted();
            operations.deleteSource(source);
            committed = true;
        } finally {
            if (published && !committed) {
                // Keep the staging link until identity has been checked. Another publisher
                // can only create a new destination after ours has disappeared.
                try {
                    if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                            && Files.isSameFile(destination, staged)) {
                        Files.delete(destination);
                    }
                } catch (IOException | RuntimeException failure) {
                    MiscUtils.getLogger().error("Could not remove an uncommitted incoming-document publication; source is preserved");
                }
            }
            // After source deletion, cleanup cannot change a committed result into failure.
            cleanup(staged);
            cleanup(directory);
        }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Incoming document publication was interrupted before acceptance");
        }
    }

    private static void cleanup(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException | RuntimeException failure) {
            MiscUtils.getLogger().warn("Could not remove private incoming-document publication staging");
        }
    }

    /** Package seam for failures and overlapping publication tests without production switches. */
    static class FileOperations {
        void copy(Path source, Path staged) throws IOException {
            Files.copy(source, staged, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        }

        void link(Path destination, Path staged) throws IOException {
            Files.createLink(destination, staged);
        }

        void deleteSource(Path source) throws IOException {
            Files.delete(source);
        }
    }
}
