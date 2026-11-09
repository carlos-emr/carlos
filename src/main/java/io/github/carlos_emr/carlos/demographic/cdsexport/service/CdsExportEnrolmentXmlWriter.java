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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportEnrolmentEventDto;
import java.io.IOException;
import java.util.List;

/** Schema-ordered enrolment body under Demographics, with the guide's stronger conditional dates. */
public final class CdsExportEnrolmentXmlWriter {
    public void write(
            List<CdsExportEnrolmentEventDto> supplied, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded -> {
                            if (supplied == null || supplied.size() > 65_536)
                                throw new IOException(
                                        "CDS_EXPORT_ENROLMENT_HISTORY_RESOURCE_LIMIT");
                            var events = List.copyOf(supplied);
                            if (events.isEmpty()) return;
                            guarded.start("cds", "Enrolment");
                            for (var event : events) {
                                guarded.start("cds", "EnrolmentHistory");
                                writeHistoryContent(event, guarded);
                                guarded.end();
                            }
                            guarded.end();
                        });
    }

    /** Content under one existing/generated history wrapper, shared by retained and native assembly. */
    void writeHistoryContent(
            CdsExportEnrolmentEventDto event, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        xml.element("cds", "EnrollmentStatus", event.status());
        xml.element("cds", "EnrollmentDate", event.start().toString());
        if (event.end() != null)
            xml.element("cds", "EnrollmentTerminationDate", event.end().toString());
        if (event.terminationReason() != null)
            xml.element("cds", "TerminationReason", event.terminationReason());
        if (event.physician() != null) {
            xml.start("cds", "EnrolledToPhysician");
            new CdsExportPublicProviderXmlWriter().write(event.physician(), xml, false);
            xml.end();
        }
    }
}
