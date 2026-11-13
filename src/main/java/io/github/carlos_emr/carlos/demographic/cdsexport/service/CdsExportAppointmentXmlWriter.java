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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAppointmentDto;
import java.io.IOException;
import java.util.Objects;

/** Pinned wire order and namespaces; supplementary native fields require separate complete reconciliation. */
public final class CdsExportAppointmentXmlWriter {
    public void write(CdsExportAppointmentDto appointment, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml).content(guarded -> writeValue(appointment, guarded));
    }

    private void writeValue(
            CdsExportAppointmentDto appointment, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        validate(appointment);
        Objects.requireNonNull(xml);
        xml.element("cds", "AppointmentTime", appointment.timing().wireTime());
        xml.element("cds", "Duration", Integer.toString(appointment.timing().durationMinutes()));
        optional(xml, "AppointmentStatus", appointment.status());
        xml.start("cds", "AppointmentDate");
        xml.element("cds_dt", "FullDate", appointment.timing().date().lexical());
        xml.end();
        if (appointment.provider() != null) {
            xml.start("cds", "Provider");
            new CdsExportPublicProviderXmlWriter().write(appointment.provider(), xml, false);
            xml.end();
        }
        optional(xml, "AppointmentPurpose", appointment.purpose());
        optional(xml, "AppointmentNotes", appointment.notes());
    }

    static void validate(CdsExportAppointmentDto appointment) throws IOException {
        Objects.requireNonNull(appointment);
        // A time with no offset is the schedule's wall-clock time at a daylight-saving transition
        // (CdsExportAppointmentTimingMapper); xs:time allows it and the importer reads it as local.
        if (appointment.status() != null
                        && !CdsExportNativeFieldAssessments.legal(appointment.status(), 250)
                || appointment.purpose() != null
                        && !CdsExportNativeFieldAssessments.legal(appointment.purpose(), 250)
                || appointment.notes() != null
                        && !CdsExportNativeFieldAssessments.legal(appointment.notes(), 32000))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException("CDS_EXPORT_APPOINTMENT_VALUES_REQUIRES_REVIEW");
    }

    private static void optional(CdsExportXmlComposer.ContentWriter xml, String name, String value)
            throws IOException {
        if (value != null) xml.element("cds", name, value);
    }
}
