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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Protected native capture receipt. This contains patient data and never belongs in status or logs. */
public record CdsExportNativeCaptureDto(int protocol, String runId, long epoch, int patient,
        String cohortSha256, Instant readStartedAt, Instant readCompletedAt, Map<String, CdsExportCapturedTableDto> tables,
        long fieldCount, long valueBytes, CdsExportStagedFileDto values, CdsExportStagedFileDto index) {
    public CdsExportNativeCaptureDto {
        tables = Map.copyOf(tables);
        if (protocol != 2 || runId == null || !UUID.fromString(runId).toString().equals(runId) || epoch < 1 || patient < 1
                || cohortSha256 == null || !cohortSha256.matches("[0-9a-f]{64}")
                || readStartedAt == null || readCompletedAt == null || readCompletedAt.isBefore(readStartedAt)
                || tables.isEmpty() || tables.size() > 256 || tables.keySet().stream().anyMatch(table -> !table.matches("[A-Za-z0-9_]{1,64}"))
                || fieldCount < 1 || fieldCount > 100_000_000 || valueBytes < 0 || values == null || index == null
                || !values.purpose().equals("SOURCE_VALUES") || !index.purpose().equals("SOURCE_INDEX")
                || values.memberId().equals(index.memberId()) || values.plaintextBytes() != valueBytes) {
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_CAPTURE_RECEIPT_INVALID");
        }
        long expectedFields = 0;
        for (var table : tables.values()) expectedFields = Math.addExact(expectedFields, Math.multiplyExact(table.rows(), table.columns().size()));
        if (expectedFields != fieldCount) throw new IllegalArgumentException("CDS_EXPORT_NATIVE_CAPTURE_RECEIPT_INVALID");
    }
    @Override public String toString() { return "CdsExportNativeCaptureDto[protected]"; }
}
