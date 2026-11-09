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

/** Finite per-patient parsing limits. Exceeding one refuses the generation without truncation. */
public record CdsExportXmlLimitsDto(long maxBytes, long maxBinaryBytes, long maxElements,
        int maxDepth, int maxIdentityValues, int maxIdentityCharacters, long maxDurationMillis) {
    public CdsExportXmlLimitsDto {
        if (maxBytes < 1 || maxBinaryBytes < 1 || maxElements < 1 || maxDepth < 1 || maxDepth > 256
                || maxIdentityValues < 1 || maxIdentityValues > 16_384 || maxIdentityCharacters < 1
                || maxIdentityCharacters > 1_048_576 || maxDurationMillis < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_XML_LIMITS_INVALID");
        }
    }
    public static CdsExportXmlLimitsDto defaults() {
        return new CdsExportXmlLimitsDto(64L << 30, 64L << 30, 20_000_000L, 256,
                16_384, 1_048_576, 7_200_000L);
    }
}
