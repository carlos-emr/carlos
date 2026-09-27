/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

/**
 * Thrown when an OHIP claim file or HTML companion file cannot be written to
 * disk. Replaces the legacy "log and return void" pattern that caused the UI
 * to report a successful claim generation while the disk write had silently
 * failed (full disk, permission denied, path traversal, etc).
 *
 * <p>Unchecked so existing call sites in {@code BillingOnDiskService} and
 * {@code OhipReportGenerationService} surface the failure without a checked
 * exception signature change. Action layers SHOULD catch this and render an
 * error banner to the operator instead of falling through to the generic
 * CARLOS error page.</p>
 *
 * @since 2026-04-29
 */
public class BillingFileWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Fixed public guidance; clinical values and filenames never enter these messages. */
    public enum Reason {
        GENERAL("The OHIP output could not be completed. Review the billing output and contact your billing administrator before retrying."),
        BUSY("An OHIP disk operation is already in progress; wait for it to finish before retrying"),
        LOCK_FAILED("OHIP output lock failed; reconcile any completed output before retrying"),
        UNCERTAIN_COMMIT("The database could not confirm the billing outcome. Generated files and rollback copies were retained. Reconcile the claims and OHIP output before retrying or submitting."),
        RESTORE_FAILED("Billing failed and some prior output could not be restored. Reconcile retained files before retrying or submitting.");

        private final String publicMessage;

        Reason(String publicMessage) {
            this.publicMessage = publicMessage;
        }

        public String publicMessage() {
            return publicMessage;
        }
    }

    private final String filename;
    private final Reason reason;

    public BillingFileWriteException(String message) {
        super(message);
        this.filename = null;
        this.reason = Reason.GENERAL;
    }

    public BillingFileWriteException(String message, Throwable cause) {
        this(message, null, cause, Reason.GENERAL);
    }

    public BillingFileWriteException(String message, String filename, Throwable cause) {
        this(message, filename, cause, Reason.GENERAL);
    }

    private BillingFileWriteException(String message, String filename, Throwable cause, Reason reason) {
        super(message, cause);
        this.filename = filename;
        this.reason = reason;
    }

    /**
     * Creates a failure with explicit, fixed operator guidance while retaining its internal cause.
     *
     * @param reason the public failure category
     * @param cause the internal failure, if any
     * @return a failure preserving the established unchecked exception contract
     */
    public static BillingFileWriteException forReason(Reason reason, Throwable cause) {
        java.util.Objects.requireNonNull(reason, "reason");
        return new BillingFileWriteException(reason.publicMessage(), null, cause, reason);
    }

    /** @return the public category, including the default for older serialized exceptions */
    public final Reason reason() {
        return reason == null ? Reason.GENERAL : reason;
    }

    /**
     * The filename that failed to write, if known, for internal recovery.
     * Do not expose it on an error page: filenames can contain clinical values.
     */
    public java.util.Optional<String> filename() {
        return java.util.Optional.ofNullable(filename);
    }
}
