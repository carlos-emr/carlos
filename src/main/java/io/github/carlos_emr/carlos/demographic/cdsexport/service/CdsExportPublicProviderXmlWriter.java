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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPublicProviderDto;
import java.io.IOException;

/** Shared body namespaces; callers own the category-specific outer element. */
public final class CdsExportPublicProviderXmlWriter {
    public void write(CdsExportPublicProviderDto provider, CdsExportXmlComposer.ContentWriter xml, boolean includeCpso) throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> writeValue(provider, guarded, includeCpso));
    }
    private void writeValue(CdsExportPublicProviderDto provider, CdsExportXmlComposer.ContentWriter xml, boolean includeCpso) throws IOException {
        java.util.Objects.requireNonNull(provider); java.util.Objects.requireNonNull(xml);
        if (!includeCpso && provider.cpsoNumber() != null) throw new IOException("CDS_EXPORT_PROVIDER_REGISTRATION_NOT_REPRESENTABLE");
        xml.start("cds", "Name"); xml.element("cds_dt", "FirstName", provider.firstName()); xml.element("cds_dt", "LastName", provider.lastName()); xml.end();
        if (provider.ohipNumber() != null) xml.element("cds", "OHIPPhysicianId", provider.ohipNumber());
        if (includeCpso && provider.cpsoNumber() != null) xml.element("cds", "PrimaryPhysicianCPSO", provider.cpsoNumber());
    }
}
