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
 * Evidence for one occurrence in one captured generation. Digests describe verified semantic
 * representations, not text-based identity or permission to disclose. The snapshot reader must
 * resolve NATIVE/FIELDS dependencies and include all mapped native rows and associations in the
 * projection digest. An arbitrary matching row hash is not sufficient mapping evidence.
 */
public record CdsExportProvenanceDto(String captureDigest, CdsExportIdentityDto identity, Origin origin,
        String mappingDigest, String originalDigest, String effectiveDigest,
        String baselineProjectionDigest, String currentProjectionDigest) {
    public enum Origin { NATIVE, IMPORTED_SOURCE_ONLY, IMPORTED_PROJECTED }

    public CdsExportProvenanceDto {
        if (captureDigest == null || !captureDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_CAPTURE_INVALID");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(origin, "origin");
        validateDigest(mappingDigest);
        validateDigest(originalDigest);
        validateDigest(effectiveDigest);
        validateDigest(baselineProjectionDigest);
        validateDigest(currentProjectionDigest);
    }

    private static void validateDigest(String value) {
        if (value != null && !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_PROVENANCE_DIGEST_INVALID");
        }
    }

    @Override public String toString() { return "CdsExportProvenanceDto[protected]"; }
}
