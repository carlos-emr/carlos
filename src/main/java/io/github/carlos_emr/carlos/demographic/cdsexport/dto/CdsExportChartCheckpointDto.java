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

/**
 * The durable pointer to the newest sealed {@code CHART_CHECKPOINT} member of one export attempt:
 * the member's opaque identifier, the SHA-256 and length of its plaintext, and how many finished
 * charts the whole chain names. The ledger records it on the attempt row; a later attempt trusts a
 * chain only when its newest member is exactly this one. Identifiers and counts only, never a
 * patient value.
 *
 * @since 2026-11-14
 */
public record CdsExportChartCheckpointDto(String memberId, String sha256, long bytes, long charts) {
    /** The staged purpose of every member of a checkpoint chain. */
    public static final String PURPOSE = "CHART_CHECKPOINT";

    private static final java.util.regex.Pattern HEX64 =
            java.util.regex.Pattern.compile("[0-9a-f]{64}");

    public CdsExportChartCheckpointDto {
        if (memberId == null
                || !HEX64.matcher(memberId).matches()
                || sha256 == null
                || !HEX64.matcher(sha256).matches()
                || bytes < 1
                || charts < 0)
            throw new IllegalArgumentException("CDS_EXPORT_CHART_CHECKPOINT_INVALID");
    }

    /** The staged member this pointer names. */
    public CdsExportStagedFileDto member() {
        return new CdsExportStagedFileDto(memberId, PURPOSE, sha256, bytes);
    }

    @Override
    public String toString() {
        return "CdsExportChartCheckpointDto[protected]";
    }
}
