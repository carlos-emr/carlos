/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/** Owns a private rollback copy of one existing billing preview. */
final class BillingOutputSnapshot {
    private final Path output;
    private Path backup;
    private final boolean existed;

    private BillingOutputSnapshot(Path output, Path backup, boolean existed) {
        this.output = output;
        this.backup = backup;
        this.existed = existed;
    }

    static BillingOutputSnapshot capture(String filename) {
        String directory = CarlosProperties.getInstance().getProperty("HOME_DIR");
        if (directory == null || directory.isBlank()) {
            throw new BillingFileWriteException("Billing output directory is not configured");
        }
        Path output = PathValidationUtils.validatePath(filename, new File(directory)).toPath();
        if (!output.getFileName().toString().equals(filename)) {
            throw new BillingFileWriteException("Billing preview must use a filename without path components");
        }
        Path backup = null;
        try {
            boolean existed = Files.exists(output);
            if (existed) {
                if (!Files.isRegularFile(output)) {
                    throw new IOException("Billing preview is not a regular file");
                }
                backup = Files.createTempFile(output.getParent(), ".ohip-preview-" + output.getFileName() + "-", ".bak");
                Files.copy(output, backup, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            return new BillingOutputSnapshot(output, backup, existed);
        } catch (IOException failure) {
            if (backup != null) {
                try { Files.deleteIfExists(backup); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw new BillingFileWriteException("Could not preserve the existing billing preview", failure);
        }
    }

    void restore() {
        try {
            if (existed) {
                // Keep the backup if restoration fails, so reconciliation remains possible.
                Files.move(backup, output, StandardCopyOption.REPLACE_EXISTING);
                backup = null;
            } else {
                Files.deleteIfExists(output);
            }
        } catch (IOException failure) {
            throw new BillingFileWriteException(
                    "Could not restore a billing preview; reconcile retained output before retrying", failure);
        }
    }

    void discard() {
        if (backup == null) return;
        try {
            Files.deleteIfExists(backup);
            backup = null;
        } catch (IOException failure) {
            // This is only an obsolete rollback copy. Never undo a committed submission
            // because deleting that copy failed; retain it and report the cleanup failure.
            MiscUtils.getLogger().warn("Could not remove an obsolete OHIP preview rollback copy ({})",
                    failure.getClass().getSimpleName());
        }
    }
}
