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

/** Exact protected XML configuration snapshot bound to one verified native/CVC generation. Not clinical coverage or disclosure authority. */
public record CdsExportPreventionConfigurationDto(String sourceManifestSha256, String configurationSha256, int xmlBytes, CdsExportStagedFileDto member) {
    public CdsExportPreventionConfigurationDto {
        if (sourceManifestSha256==null||!sourceManifestSha256.matches("[0-9a-f]{64}")||configurationSha256==null||!configurationSha256.matches("[0-9a-f]{64}")
                ||xmlBytes<1||xmlBytes>1_048_576||member==null||!"SOURCE_PREVENTION_CONFIG".equals(member.purpose())||member.plaintextBytes()!=76L+xmlBytes)
            throw new IllegalArgumentException("CDS_EXPORT_PREVENTION_CONFIG_RECEIPT_INVALID");
    }
    @Override public String toString(){return "CdsExportPreventionConfigurationDto[protected]"; }
}
