/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Validated billing outputs published only after a complete, successfully closed write. */
final class BillingOutputFiles {
    private BillingOutputFiles() { }

    static Path path(String filename) {
        try {
            Path output = PathValidationUtils.validatePath(filename,
                    PathValidationUtils.getRequiredHomeDirectory()).toPath();
            if (!output.getFileName().toString().equals(filename)) {
                throw new BillingFileWriteException("Billing output must use a filename without path components");
            }
            return output;
        } catch (IOException failure) {
            throw new BillingFileWriteException("Billing output directory is unavailable", failure);
        }
    }

    // The directory is the validated administrator-configured HOME_DIR. The suffix is generated
    // exclusively by the filesystem, and the prefix contains only the validated basename.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Internal helper accepts a HOME_DIR-contained validated output; createTempFile generates an exclusive sibling name")
    static Path temporarySibling(Path output, String prefix, String suffix) throws IOException {
        return Files.createTempFile(output.getParent(), prefix + output.getFileName() + "-", suffix);
    }

    /** Complete files are published atomically. A private link or open descriptor pins their identity until commit. */
    static final class NewFiles implements AutoCloseable {
        private final List<Publication> published = new ArrayList<>();

        Publication write(String filename, String contents, Charset charset) {
            Path output = path(filename);
            Path temporary = null;
            try {
                temporary = temporarySibling(output, ".ohip-write-", ".tmp");
                try (var stream = new FileOutputStream(temporary.toFile());
                     var writer = new BufferedWriter(new OutputStreamWriter(stream, charset))) {
                    writer.write(contents);
                    writer.newLine();
                    writer.flush();
                    stream.getChannel().force(true);
                }
                var receipt = new Publication(output, temporary);
                receipt.publish();
                published.add(receipt);
                temporary = null; // The receipt now owns the link or the pinned inode.
                try (var directory = FileChannel.open(output.getParent(), StandardOpenOption.READ)) {
                    directory.force(true);
                }
                return receipt;
            } catch (IOException | RuntimeException failure) {
                if (temporary != null) {
                    try { Files.deleteIfExists(temporary); }
                    catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                }
                if (failure instanceof FileAlreadyExistsException) {
                    throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.NEW_OUTPUT_OCCUPIED, failure);
                }
                if (failure instanceof AtomicMoveNotSupportedException) {
                    throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.NEW_OUTPUT_UNSUPPORTED, failure);
                }
                throw new BillingFileWriteException("Could not create a new OHIP billing output", failure);
            }
        }

        void rollback(RuntimeException originalFailure) {
            boolean incomplete = false;
            for (Publication receipt : published) {
                try { receipt.withdraw(); }
                catch (IOException | RuntimeException cleanup) {
                    incomplete = true;
                    originalFailure.addSuppressed(cleanup);
                }
            }
            if (incomplete) {
                throw BillingFileWriteException.forReason(BillingFileWriteException.Reason.RESTORE_FAILED, originalFailure);
            }
        }

        @Override
        public void close() {
            for (Publication receipt : published) receipt.release();
        }
    }

    static final class Publication {
        private final Path output;
        private Path witness;
        private FileChannel pinned;
        private Object fileKey;

        private Publication(Path output, Path witness) {
            this.output = output;
            this.witness = witness;
        }

        private void publish() throws IOException {
            try {
                Files.createLink(output, witness);
                return;
            } catch (FileAlreadyExistsException occupied) {
                throw occupied;
            } catch (IOException | UnsupportedOperationException noLink) {
                // Some network shares allow atomic no-replace rename but do not allow hard links.
                pinned = FileChannel.open(witness, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                try {
                    fileKey = Files.readAttributes(witness, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
                    if (fileKey == null) throw new AtomicMoveNotSupportedException(witness.toString(), output.toString(),
                            "Storage does not provide a persistent file identity");
                    LinuxNoReplaceMove.move(witness, output);
                    witness = null; // The open descriptor prevents inode reuse until database finalization.
                } catch (IOException | RuntimeException failure) {
                    failure.addSuppressed(noLink);
                    try { pinned.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                    pinned = null;
                    throw failure;
                }
            }
        }

        private boolean owns(Path candidate) throws IOException {
            try {
                var actual = Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return actual.isRegularFile() && (pinned == null ? Files.isSameFile(candidate, witness) : fileKey.equals(actual.fileKey()));
            } catch (NoSuchFileException missing) {
                return false;
            }
        }

        void withdraw() throws IOException {
            if ((witness == null && pinned == null) || !owns(output)) return;
            Path detached = temporarySibling(output, ".ohip-rollback-", ".tmp");
            boolean preserve = false;
            try {
                // POSIX has no unlink-if-same-inode. Inspect what the atomic move actually removed
                // before unlinking it, even if an external filesystem writer raced the earlier check.
                try { Files.move(output, detached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (NoSuchFileException disappeared) { return; }
                if (!owns(detached)) {
                    preserve = true;
                    // A newer target wins; keep the detached entry for recovery.
                    if (pinned != null) LinuxNoReplaceMove.move(detached, output);
                    else Files.createLink(output, detached);
                    preserve = false;
                }
            } finally {
                if (!preserve) Files.deleteIfExists(detached);
            }
        }

        private void release() {
            try {
                if (witness != null) Files.deleteIfExists(witness);
                else if (pinned != null) pinned.close();
            }
            catch (IOException | RuntimeException cleanup) {
                MiscUtils.getLogger().warn("Could not clear a private OHIP publication link ({})", cleanup.getClass().getSimpleName());
            }
            witness = null;
            pinned = null;
        }
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_OUT", justification = "Writes only an exclusive generated temporary sibling of a validated HOME_DIR-contained basename, then atomically replaces that validated output")
    static void write(String filename, String contents, Charset charset, String label) {
        Path output = path(filename);
        Path temporary = null;
        try {
            temporary = temporarySibling(output, ".ohip-write-", ".tmp");
            try (var stream = new FileOutputStream(temporary.toFile());
                 var writer = new BufferedWriter(new OutputStreamWriter(stream, charset))) {
                writer.write(contents);
                writer.newLine();
            }
            if (Files.exists(output)) {
                var attributes = Files.getFileAttributeView(output, java.nio.file.attribute.PosixFileAttributeView.class);
                if (attributes != null) Files.setPosixFilePermissions(temporary, attributes.readAttributes().permissions());
            }
            // No non-atomic fallback: unsupported filesystems must preserve the old download.
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw new BillingFileWriteException("Failed to write " + label + ": " + filename, failure);
        }
    }
}
