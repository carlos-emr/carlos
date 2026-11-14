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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * The patient's captured values member, authenticated once for all of its binary values (review
 * R2). Reopening the member for each attachment decrypted it from byte 0 to the attachment's
 * offset and then drained it to its end, so N attachments cost N complete passes over everything
 * captured for the patient. Opening this scope verifies every chunk of the member once and pins
 * its descriptor; each value then decrypts only the chunks of its own range, each under its own
 * authentication tag and chunk index, and its SHA-256 is checked by both the range and the
 * enclosing stream. Closing verifies the whole member again, terminator and length included,
 * before anything read through it is accepted with the patient's output.
 *
 * <p>Used by the native document reports and the HRM reports.
 */
final class CdsExportCapturedValues {
    private final CdsExportStagingService store;
    private final CdsExportStagedFileDto member;
    private CdsExportStagingService.VerifiedRanges ranges;
    private boolean closed;

    CdsExportCapturedValues(CdsExportStagingService store, CdsExportStagedFileDto member) {
        this.store = Objects.requireNonNull(store);
        this.member = Objects.requireNonNull(member);
    }

    InputStream open(
            CdsExportCapturedFieldDto field,
            CdsExportAuthenticatedRangeInputStream.Checkpoint checkpoint,
            Runnable authorize)
            throws IOException {
        if (closed || field.kind() != CdsExportCapturedFieldDto.Kind.BINARY)
            throw new IOException("CDS_EXPORT_REPORT_OCCURRENCE_INVALID");
        if (ranges == null) ranges = store.verifiedRanges(member);
        return new CdsExportAuthenticatedRangeInputStream(
                ranges.open(field.offset(), field.bytes(), field.sha256()),
                0,
                field.bytes(),
                field.sha256(),
                checkpoint,
                authorize);
    }

    void close() throws IOException {
        closed = true;
        var open = ranges;
        ranges = null;
        if (open != null) open.close();
    }
}
