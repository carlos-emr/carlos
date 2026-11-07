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

import java.io.IOException;
import java.util.Map;

/** Bounded public references from one authenticated patient capture; a provider ID never binds another generation. */
public record CdsExportPublicProviderLookupsDto(String sourceManifestSha256, Map<String, CdsExportCapturedTextRowDto> providers) {
    public CdsExportPublicProviderLookupsDto {
        if (sourceManifestSha256==null||!sourceManifestSha256.matches("[0-9a-f]{64}")||providers==null||providers.size()>65536)throw new IllegalArgumentException("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_INVALID"); providers=Map.copyOf(providers);
    }
    /** Refuses cross-generation lookup reuse before a public clinical value can be emitted. */
    public void requireGeneration(String expected)throws IOException {if (!sourceManifestSha256.equals(expected))throw new IOException("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_GENERATION_INVALID"); }
    public CdsExportCapturedTextRowDto find(String id){return id==null?null:providers.get(id); }
    @Override public String toString(){return "CdsExportPublicProviderLookupsDto[protected]"; }
}
