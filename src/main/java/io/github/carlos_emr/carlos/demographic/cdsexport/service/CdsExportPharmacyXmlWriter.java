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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;

/** Schema-ordered preferred pharmacy. Extra native values and unresolved history refuse output. */
public final class CdsExportPharmacyXmlWriter {
    public void write(CdsExportPharmacyMapper.Result mapping, CdsExportXmlComposer.ContentWriter xml) throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> {
            if (mapping == null || !mapping.nativeRepresentable()) throw new IOException("CDS_EXPORT_PHARMACY_MAPPING_REQUIRES_REVIEW");
            var pharmacy = mapping.preferred(); if (pharmacy == null) return;
            guarded.start("cds", "PreferredPharmacy"); guarded.element("cds", "Name", pharmacy.name());
            if (pharmacy.address() != null) {
                var address = pharmacy.address(); guarded.start("cds", "Address"); guarded.attribute("", "addressType", "M"); guarded.start("cds_dt", "Structured");
                optional(guarded, "cds_dt", "Line1", address.line1()); optional(guarded, "cds_dt", "City", address.city());
                optional(guarded, "cds_dt", "CountrySubdivisionCode", address.subdivision());
                if (address.postalCode() != null) {
                    guarded.start("cds_dt", "PostalZipCode"); guarded.element("cds_dt", address.subdivision() != null && address.subdivision().startsWith("US-") ? "ZipCode" : "PostalCode", address.postalCode()); guarded.end();
                }
                guarded.end(); guarded.end();
            }
            phone(guarded, "PhoneNumber", pharmacy.phone()); phone(guarded, "FaxNumber", pharmacy.fax());
            optional(guarded, "cds", "EmailAddress", pharmacy.email()); guarded.end();
        });
    }
    private static void phone(CdsExportXmlComposer.ContentWriter xml, String element, CdsExportDemographicMappingDto.Phone value) throws IOException {
        if (value == null) return; xml.start("cds", element); xml.attribute("", "phoneNumberType", "W");
        xml.element("cds_dt", "phoneNumber", value.number()); optional(xml, "cds_dt", "extension", value.extension()); xml.end();
    }
    private static void optional(CdsExportXmlComposer.ContentWriter xml, String namespace, String element, String value) throws IOException {
        if (value != null) xml.element(namespace, element, value);
    }
}
