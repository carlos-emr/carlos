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

/** Opaque physical member and authenticated plaintext identity. No clinical filename is stored on disk. */
public record CdsExportStagedFileDto(String memberId, String purpose, String plaintextSha256, long plaintextBytes) {
    public CdsExportStagedFileDto {
        if (memberId == null || !memberId.matches("[0-9a-f]{64}") || purpose == null
                || !purpose.matches("[A-Z][A-Z0-9_]{0,63}") || plaintextSha256 == null
                || !plaintextSha256.matches("[0-9a-f]{64}") || plaintextBytes < 0) {
            throw new IllegalArgumentException("CDS_EXPORT_STAGED_FILE_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportStagedFileDto[protected]"; }
}
