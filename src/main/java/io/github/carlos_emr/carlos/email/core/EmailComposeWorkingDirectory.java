/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Owns the generated PDF artifacts for one email compose/send operation.
 *
 * <p>The browser receives opaque preview and submission tokens, never this directory path. Closing
 * the owner recursively removes only this dedicated directory and is safe to call more than once.</p>
 *
 * @since 2026-08-14
 */
@SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "Paths are server-generated renderer output and are contained in a private random directory")
public final class EmailComposeWorkingDirectory implements AutoCloseable {
    public static final String DIRECTORY_PREFIX = "email-compose-";
    public static final String ACTIVE_LEASE_FILE_NAME = ".active-lease";
    /** Maximum lifetime accepted for an on-disk lease when no live JVM owner remains. */
    public static final long ACTIVE_LEASE_MILLIS =
            EmailComposeSubmissionStateService.PENDING_EMAIL_COMPOSE_STATE_MAX_AGE_MILLIS
                    + 5L * 60 * 1000;

    private static final Logger logger = MiscUtils.getLogger();
    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<Path> ACTIVE_DIRECTORIES = ConcurrentHashMap.newKeySet();
    private static final LongCounter CLEANUP_SUCCESSES = GlobalOpenTelemetry.getMeter(
                    EmailComposeWorkingDirectory.class.getName())
            .counterBuilder("carlos.email.compose_temp.cleanup.success")
            .setDescription("Email compose working directories removed")
            .build();
    private static final LongCounter CLEANUP_FAILURES = GlobalOpenTelemetry.getMeter(
                    EmailComposeWorkingDirectory.class.getName())
            .counterBuilder("carlos.email.compose_temp.cleanup.failure")
            .setDescription("Email compose working directory cleanup failures")
            .build();

    private final Path applicationTempRoot;
    private final Path directory;
    private final AtomicBoolean closed = new AtomicBoolean();

    private EmailComposeWorkingDirectory(Path applicationTempRoot, Path directory) {
        this.applicationTempRoot = applicationTempRoot;
        this.directory = directory;
        ACTIVE_DIRECTORIES.add(directory);
    }

    /** Creates a private working directory below the CARLOS application temp root. */
    public static EmailComposeWorkingDirectory create() throws IOException {
        Path systemTemp = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
        return create(systemTemp.resolve(PathValidationUtils.APPLICATION_TEMP_ROOT_NAME));
    }

    /** Package-private factory for focused filesystem tests. */
    static EmailComposeWorkingDirectory create(Path requestedApplicationTempRoot) throws IOException {
        Path root = createSecureDirectory(requestedApplicationTempRoot);
        FileAttribute<Set<PosixFilePermission>> permissions =
                PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS);
        Path workingDirectory;
        try {
            workingDirectory = Files.createTempDirectory(root, DIRECTORY_PREFIX, permissions);
        } catch (UnsupportedOperationException e) {
            workingDirectory = Files.createTempDirectory(root, DIRECTORY_PREFIX);
        }
        try {
            applyPermissionsIfSupported(workingDirectory, OWNER_DIRECTORY_PERMISSIONS);
            createActiveLease(workingDirectory);
        } catch (IOException | RuntimeException e) {
            deleteAfterFailedCreation(workingDirectory.resolve(ACTIVE_LEASE_FILE_NAME), e);
            deleteAfterFailedCreation(workingDirectory, e);
            throw e;
        }
        return new EmailComposeWorkingDirectory(root.toRealPath(), workingDirectory.toRealPath());
    }

    /**
     * Moves a disposable CARLOS temp PDF into this directory, or copies a durable source PDF so
     * source documents are never deleted.
     *
     * @param source generated PDF path returned by a server-side renderer
     * @return the owned path to use for preview and sending
     */
    public synchronized Path adoptGeneratedPdf(Path source) throws IOException {
        ensureOpen();
        if (source == null || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generated email attachment is not a regular file");
        }
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IOException("Generated email attachment is not a PDF");
        }

        Path realSource = source.toRealPath();
        if (realSource.startsWith(directory)) {
            return realSource;
        }

        Path target = createSecurePdf();
        boolean disposableSource = PathValidationUtils.isInAllowedTempDirectory(realSource.toFile());
        try {
            if (disposableSource) {
                try {
                    Path ownedPdf = Files.move(realSource, target, StandardCopyOption.REPLACE_EXISTING);
                    applyPermissionsIfSupported(ownedPdf, OWNER_FILE_PERMISSIONS);
                    return ownedPdf;
                } catch (IOException moveFailure) {
                    Files.copy(realSource, target, StandardCopyOption.REPLACE_EXISTING);
                    Files.delete(realSource);
                    applyPermissionsIfSupported(target, OWNER_FILE_PERMISSIONS);
                    return target;
                }
            }
            Files.copy(realSource, target, StandardCopyOption.REPLACE_EXISTING);
            applyPermissionsIfSupported(target, OWNER_FILE_PERMISSIONS);
            return target;
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw new IOException("Unable to take ownership of generated email attachment", e);
        }
    }

    /** Returns whether a path is an artifact in this working directory. */
    public boolean owns(Path path) {
        return path != null && path.toAbsolutePath().normalize().startsWith(directory);
    }

    /**
     * Returns whether this JVM still has a live owner for an email compose directory.
     *
     * <p>The orphan sweep uses this process-local ownership check in addition to the on-disk lease.
     * Unlike the bounded lease, ownership remains active for the complete render/send operation and
     * is released by {@link #close()}. A process crash clears the registry, so it cannot permanently
     * exempt abandoned directories from the on-disk orphan sweep.</p>
     */
    public static boolean isActivelyOwned(Path candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            return ACTIVE_DIRECTORIES.contains(candidate.toRealPath());
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /** Test/support hook that does not expose the directory to browser state. */
    Path path() {
        return directory;
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ACTIVE_DIRECTORIES.remove(directory);
        try {
            deleteOwnedDirectory();
            CLEANUP_SUCCESSES.add(1);
        } catch (IOException | SecurityException e) {
            CLEANUP_FAILURES.add(1);
            logger.warn("Unable to remove an email compose working directory");
        }
    }

    private Path createSecurePdf() throws IOException {
        FileAttribute<Set<PosixFilePermission>> permissions =
                PosixFilePermissions.asFileAttribute(OWNER_FILE_PERMISSIONS);
        Path target;
        try {
            target = Files.createTempFile(directory, "artifact-", ".pdf", permissions);
        } catch (UnsupportedOperationException e) {
            target = Files.createTempFile(directory, "artifact-", ".pdf");
        }
        try {
            applyPermissionsIfSupported(target, OWNER_FILE_PERMISSIONS);
        } catch (IOException | RuntimeException e) {
            deleteAfterFailedCreation(target, e);
            throw e;
        }
        return target;
    }

    private static void deleteAfterFailedCreation(Path path, Throwable originalFailure) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void deleteOwnedDirectory() throws IOException {
        if (!Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory)) {
            throw new SecurityException("Email compose working directory was replaced by a symbolic link");
        }
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.getParent().equals(applicationTempRoot)) {
            throw new SecurityException("Email compose working directory escaped its application temp root");
        }

        Files.walkFileTree(realDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("Email compose working directory is closed");
        }
    }

    private static Path createSecureDirectory(Path requestedRoot) throws IOException {
        Path normalized = requestedRoot.toAbsolutePath().normalize();
        if (Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(normalized)
                    || !Files.isDirectory(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("CARLOS application temp root is not a secure directory");
            }
        } else {
            FileAttribute<Set<PosixFilePermission>> permissions =
                    PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS);
            try {
                Files.createDirectories(normalized, permissions);
            } catch (UnsupportedOperationException e) {
                Files.createDirectories(normalized);
            }
        }
        java.nio.file.attribute.UserPrincipal owner =
                Files.getOwner(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        String jvmUser = System.getProperty("user.name");
        if (jvmUser != null && owner != null && !jvmUser.equals(owner.getName())) {
            throw new IOException("CARLOS application temp root is owned by another user");
        }
        applyPermissionsIfSupported(normalized, OWNER_DIRECTORY_PERMISSIONS);
        return normalized.toRealPath();
    }

    private static void createActiveLease(Path workingDirectory) throws IOException {
        Path leaseFile = workingDirectory.resolve(ACTIVE_LEASE_FILE_NAME);
        String expiresAt = Long.toString(System.currentTimeMillis() + ACTIVE_LEASE_MILLIS);
        Files.writeString(leaseFile, expiresAt, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        applyPermissionsIfSupported(leaseFile, OWNER_FILE_PERMISSIONS);
    }

    private static void applyPermissionsIfSupported(Path path, Set<PosixFilePermission> permissions)
            throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException e) {
            // The platform does not expose POSIX permissions; secure creation still uses a unique path.
        }
    }
}
