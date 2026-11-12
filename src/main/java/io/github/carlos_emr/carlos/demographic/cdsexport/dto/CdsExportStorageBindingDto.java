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

/** Stable server-owned storage/key identifiers and fingerprint; never contains key material or a filesystem path. */
public record CdsExportStorageBindingDto(String rootId, String keyId, String keySha256) {
    public CdsExportStorageBindingDto {
        if (rootId == null || !rootId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || keyId == null || !keyId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || keySha256 == null || !keySha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("CDS_EXPORT_STORAGE_BINDING_INVALID");
        }
    }
    @Override public String toString() { return "CdsExportStorageBindingDto[protected]"; }
}
