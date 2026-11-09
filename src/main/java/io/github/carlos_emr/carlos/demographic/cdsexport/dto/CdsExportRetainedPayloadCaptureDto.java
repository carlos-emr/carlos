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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.UUID;

/** Frozen selected field ranges, bound to the exact native/retained metadata generation. Not clinical coverage proof. */
public record CdsExportRetainedPayloadCaptureDto(int protocol, String runId, long epoch, int patient,
        String cohortSha256, String sourceManifestSha256, String fieldsSha256, long fields, long payloads, long payloadBytes,
        CdsExportStagedFileDto values, CdsExportStagedFileDto index) {
    public CdsExportRetainedPayloadCaptureDto {
        if (protocol != 1 || runId == null || !UUID.fromString(runId).toString().equals(runId) || epoch < 1 || patient < 1
                || cohortSha256 == null || !cohortSha256.matches("[0-9a-f]{64}")
                || sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}")
                || fieldsSha256 == null || !fieldsSha256.matches("[0-9a-f]{64}")
                || fields < 0 || fields > 100_000_000 || payloads < 0 || payloads > fields || payloadBytes < 0
                || values == null || index == null || !values.purpose().equals("RETAINED_VALUES") || !index.purpose().equals("RETAINED_INDEX")
                || values.memberId().equals(index.memberId()) || values.plaintextBytes() != payloadBytes) {
            throw new IllegalArgumentException("CDS_EXPORT_RETAINED_PAYLOAD_CAPTURE_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportRetainedPayloadCaptureDto[protected]"; }
}
