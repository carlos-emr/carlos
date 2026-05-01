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

    private final String filename;

    public BillingFileWriteException(String message) {
        super(message);
        this.filename = null;
    }

    public BillingFileWriteException(String message, Throwable cause) {
        super(message, cause);
        this.filename = null;
    }

    public BillingFileWriteException(String message, String filename, Throwable cause) {
        super(message, cause);
        this.filename = filename;
    }

    /**
     * The filename that failed to write, if known. Callers reading the
     * rendered error page can pull this out programmatically (e.g. to
     * render "Failed to write `ohip_2026_04_30.txt`" in a banner).
     */
    public java.util.Optional<String> filename() {
        return java.util.Optional.ofNullable(filename);
    }
}
