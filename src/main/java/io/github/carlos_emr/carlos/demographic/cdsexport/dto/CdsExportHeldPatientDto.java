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

import java.util.Objects;

/**
 * A selected patient whose chart the job could not represent completely, with the safe reason code.
 * The job still packages every other selected patient; held patients are listed in the protected
 * diagnostics and in the encrypted ReadMe, and the run never reports a complete migration.
 *
 * @since 2026-11-13
 */
public record CdsExportHeldPatientDto(int patient, String code) {
    public CdsExportHeldPatientDto {
        Objects.requireNonNull(code, "code");
        if (patient < 1 || !code.matches("CDS_EXPORT_[A-Z0-9_]{1,85}"))
            throw new IllegalArgumentException("CDS_EXPORT_HELD_PATIENT_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportHeldPatientDto[protected]";
    }
}
