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

/** One accounted source occurrence; output digest must bind its actual staged representation. */
public record CdsExportDispositionDto(String captureDigest, CdsExportIdentityDto identity, Disposition disposition,
        String outputDigest, Blocker blocker) {
    public enum Disposition { EMITTED, BLOCKED }
    public enum Blocker {
        RECONCILIATION_REQUIRED, REPRESENTATION_UNSUPPORTED, SOURCE_UNREADABLE,
        ACCESS_REVOKED, RENDER_INCOMPLETE, VALIDATION_FAILED, RESOURCE_LIMIT
    }

    public CdsExportDispositionDto {
        if (captureDigest == null || !captureDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_INVALID");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(disposition, "disposition");
        boolean valid = switch (disposition) {
            case EMITTED -> blocker == null && outputDigest != null && outputDigest.matches("[0-9a-f]{64}");
            case BLOCKED -> blocker != null && outputDigest == null;
        };
        if (!valid) throw new IllegalArgumentException("CDS_EXPORT_DISPOSITION_INVALID");
    }

    @Override public String toString() { return "CdsExportDispositionDto[protected]"; }
}
