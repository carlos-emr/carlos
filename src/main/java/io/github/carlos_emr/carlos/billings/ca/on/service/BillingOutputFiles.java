/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
