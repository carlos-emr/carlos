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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.nio.file.Path;
import java.util.UUID;

/** Captured original/effective evidence; field-unit identity is deliberately distinct from a native unit key. */
public record CdsExportRetainedFieldDto(String id, String runId, String sourcePatientKey, String fieldUnitKey,
        CdsCategory category, String sourcePath, long occurrence, String namespace, String valueType, String precision,
        String originalInline, String logicalInline, Payload originalPayload) {
    public record Payload(String location, String sha256, long offset, long bytes) {
        public Payload {
            if (location == null || location.isEmpty() || location.length() > 1024 || location.codePoints().anyMatch(Character::isISOControl)
                    || !Path.of(location).isAbsolute() || !Path.of(location).equals(Path.of(location).normalize())
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}") || offset < 0 || bytes < 0 || bytes > Long.MAX_VALUE - offset) invalid();
        }
        @Override public String toString() { return "CdsExportRetainedFieldDto.Payload[protected]"; }
    }
    public CdsExportRetainedFieldDto {
        if (id == null || !id.matches("[0-9a-f]{64}") || runId == null || !UUID.fromString(runId).toString().equals(runId)
                || sourcePatientKey == null || !sourcePatientKey.matches("[0-9a-f]{64}") || fieldUnitKey == null || !fieldUnitKey.matches("[0-9a-f]{64}")
                || category == null || sourcePath == null || !sourcePath.startsWith("/") || !text(sourcePath, 2048, false) || occurrence < 0
                || !text(namespace, 250, true) || !text(valueType, 4608, false) || precision != null && !text(precision, 32, false)
                || (originalInline == null) == (originalPayload == null) || originalInline != null && originalInline.length() > 32768
                || logicalInline != null && logicalInline.length() > 65536) invalid();
    }
    private static boolean text(String value, int maximum, boolean empty) {
        return value != null && (empty || !value.isEmpty()) && value.length() <= maximum && value.codePoints().noneMatch(Character::isISOControl);
    }
    private static void invalid() { throw new IllegalArgumentException("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW"); }
    @Override public String toString() { return "CdsExportRetainedFieldDto[protected]"; }
}
