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

/** Three fully closed encrypted members; no publication or clinical coverage claim is implied. */
public record CdsExportPatientCaptureDto(CdsExportNativeCaptureDto capture, CdsExportStagedFileDto manifest) {
    public CdsExportPatientCaptureDto {
        Objects.requireNonNull(capture); Objects.requireNonNull(manifest);
        if (!manifest.purpose().equals("SOURCE_MANIFEST") || manifest.memberId().equals(capture.values().memberId())
                || manifest.memberId().equals(capture.index().memberId())) throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_MANIFEST_INVALID");
    }
    @Override public String toString() { return "CdsExportPatientCaptureDto[protected]"; }
}
