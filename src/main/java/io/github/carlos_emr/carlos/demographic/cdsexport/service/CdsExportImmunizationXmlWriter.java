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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportImmunizationDto;
import java.io.IOException;

/** Schema-ordered standard vaccine event. Native screening entries and program restrictions require classification before this writer. */
public final class CdsExportImmunizationXmlWriter {
    public void write(CdsExportImmunizationDto value, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> writeValue(value, guarded));
    }

    private void writeValue(CdsExportImmunizationDto value, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        validate(value);
        xml.element("cds", "ImmunizationName", value.name());
        optional(xml, "ImmunizationType", value.type());
        optional(xml, "Manufacturer", value.manufacturer());
        optional(xml, "LotNumber", value.lot());
        optional(xml, "Route", value.route());
        optional(xml, "Site", value.site());
        optional(xml, "Dose", value.dose());
        if (value.code() != null) {
            xml.start("cds", "ImmunizationCode");
            xml.element("cds_dt", "CodingSystem", value.codeSystem());
            xml.element("cds_dt", "value", value.code());
            xml.end();
        }
        if (value.date().precision() != CdsExportDateDto.Precision.ABSENT) {
            xml.start("cds", "Date");
            CdsExportDateXmlWriter.content(xml, value.date(), true);
            xml.end();
        }
        xml.start("cds", "RefusedFlag");
        xml.element("cds_dt", "ynIndicatorsimple", value.refused() ? "Y" : "N");
        xml.end();
        optional(xml, "Instructions", value.instructions());
        optional(xml, "Notes", value.notes());
    }

    static void validate(CdsExportImmunizationDto value) throws IOException {
        java.util.Objects.requireNonNull(value);
        if (value.name() == null || value.name().isBlank()) invalid();
        text(value.name(), 120);
        text(value.type(), 20);
        text(value.manufacturer(), 120);
        text(value.lot(), 120);
        text(value.route(), 120);
        text(value.site(), 120);
        text(value.dose(), 120);
        text(value.instructions(), 250);
        text(value.notes(), 32000);
        if (value.type() != null && !CdsExportImmunizationVocabulary.TYPES.contains(value.type()))
            invalid();
        if (value.code() != null
                && (!"DIN".equals(value.codeSystem()) || !value.code().matches("[0-9]{8}")))
            invalid();
    }

    private static void text(String value, int maximum) throws IOException {
        if (value != null && !CdsExportNativeFieldAssessments.legal(value, maximum)) invalid();
    }

    private static void optional(CdsExportXmlComposer.ContentWriter xml, String name, String value)
            throws IOException {
        if (value != null) xml.element("cds", name, value);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
    }
}
