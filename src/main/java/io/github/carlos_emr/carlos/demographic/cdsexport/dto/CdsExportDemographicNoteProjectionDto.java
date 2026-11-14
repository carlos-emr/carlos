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

/** One frozen generation's base demographic and patient-note projection; other related/history inputs still need independent coverage. */
public record CdsExportDemographicNoteProjectionDto(String sourceManifestSha256, CdsExportDemographicMappingDto demographic,
        long noteRows, CdsExportPatientNoteMappingDto note) {
    public CdsExportDemographicNoteProjectionDto {
        if (sourceManifestSha256 == null || !sourceManifestSha256.matches("[0-9a-f]{64}") || demographic == null
                || noteRows < 0 || noteRows > 1 || (noteRows == 0) != (note == null) || note != null && note.patient() != demographic.patient())
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_NOTE_PROJECTION_INVALID");
    }
    public boolean representable() { return demographic.baseRowRepresentable() && (note == null || note.representable()); }
    @Override public String toString() { return "CdsExportDemographicNoteProjectionDto[protected]"; }
}
