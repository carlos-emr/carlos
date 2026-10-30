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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

/** Explicit per-import resource admission limits; changing these is not a memory-management fix. */
public record CdsInputLimitsDto(long maxSourceBytes, long maxExpandedBytes, long maxEntries,
        int maxXmlDepth, int maxBoundedScalarCharacters, long maxBinaryBytes,
        long minimumFreeDiskBytes, long maxDurationMillis, int maxPathCharacters, double maxCompressionRatio,
        int maxNamespaceAliases, int maxUniqueXmlNames, int maxXmlNameCharacters, int maxNamespaceUriCharacters) {
    public CdsInputLimitsDto {
        if (maxSourceBytes < 1 || maxExpandedBytes < 1 || maxEntries < 1 || maxXmlDepth < 4
                || maxBoundedScalarCharacters < 65536 || maxBinaryBytes < 1
                || minimumFreeDiskBytes < 0 || maxDurationMillis < 1 || maxPathCharacters < 256
                || !Double.isFinite(maxCompressionRatio) || maxCompressionRatio < 1
                || maxNamespaceAliases < 1 || maxUniqueXmlNames < 1 || maxXmlNameCharacters < 64
                || maxNamespaceUriCharacters < 128) {
            throw new IllegalArgumentException("Invalid CDS input resource limits");
        }
    }

    public CdsInputLimitsDto(long maxSourceBytes, long maxExpandedBytes, long maxEntries, int maxXmlDepth,
            int maxBoundedScalarCharacters, long maxBinaryBytes, long minimumFreeDiskBytes,
            long maxDurationMillis, int maxPathCharacters) {
        this(maxSourceBytes, maxExpandedBytes, maxEntries, maxXmlDepth, maxBoundedScalarCharacters,
                maxBinaryBytes, minimumFreeDiskBytes, maxDurationMillis, maxPathCharacters, 200);
    }

    public CdsInputLimitsDto(long maxSourceBytes, long maxExpandedBytes, long maxEntries, int maxXmlDepth,
            int maxBoundedScalarCharacters, long maxBinaryBytes, long minimumFreeDiskBytes,
            long maxDurationMillis, int maxPathCharacters, double maxCompressionRatio) {
        this(maxSourceBytes, maxExpandedBytes, maxEntries, maxXmlDepth, maxBoundedScalarCharacters,
                maxBinaryBytes, minimumFreeDiskBytes, maxDurationMillis, maxPathCharacters,
                maxCompressionRatio, 128, 4096, 256, 4096);
    }

    /** Conservative operational defaults, overridable by the deployment's measured workload policy. */
    public static CdsInputLimitsDto defaults() {
        return new CdsInputLimitsDto(64L << 30, 512L << 30, 2_000_000, 256, 65536,
                64L << 30, 512L << 20, 24L * 60 * 60 * 1000, 8192, 200);
    }

    /** XSD identity tables retain values across the document, including dynamic xsi:type values. */
    public int maxSchemaIdentityValues() { return 16_384; }

    /** Aggregate lexical UTF16 units admitted before the validator can retain identity values. */
    public int maxSchemaIdentityCharacters() { return 1 << 20; }
}
