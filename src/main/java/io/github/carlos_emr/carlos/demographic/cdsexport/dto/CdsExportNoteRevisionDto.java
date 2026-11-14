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

/** Exact bounded revision metadata; no note body, history, password or inferred current version. */
public record CdsExportNoteRevisionDto(long noteId, int patient, String uuid, String observationDate,
        String updateDate, String archived, String signed) {
    public CdsExportNoteRevisionDto {
        if (noteId < 1 || patient < 1) throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
        for (String value : new String[] {uuid, observationDate, updateDate, archived, signed})
            if (value != null && (value.length() > 256 || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
                    || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256))
                throw new IllegalArgumentException("CDS_EXPORT_NOTE_REVISION_METADATA_INVALID");
    }
    @Override public String toString() { return "CdsExportNoteRevisionDto[protected]"; }
}
