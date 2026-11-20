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

import java.util.HashSet;
import java.util.Set;

/**
 * The capture tables of a patient's Hospital Report Manager (HRM) reports. Every one of them is
 * read in the Reports scope, and each reader requires all of them, so a report is never captured
 * without its links, comments, subclasses, provider routes or stored message file.
 *
 * @since 2026-11-13
 */
final class CdsExportHospitalReportSources {
    /** The patient links ({@code HRMDocumentToDemographic}). */
    static final String LINKS = "HRMDocumentToDemographic";

    /** The reports ({@code HRMDocument}). */
    static final String REPORTS = "HRMDocument";

    /** Comments written on a report. */
    static final String COMMENTS = "HRMDocumentComment";

    /** Accompanying subclasses copied from a report's OBR content. */
    static final String SUBCLASSES = "HRMDocumentSubClass";

    /** Provider routes and sign-offs. */
    static final String ROUTES = "HRMDocumentToProvider";

    /** Logical capture of each report's stored message file. */
    static final String FILES = "hrm_report_files";

    /** The row tables, read as text rows. */
    static final Set<String> ROWS = Set.of(LINKS, REPORTS, COMMENTS, SUBCLASSES, ROUTES);

    /** Every HRM capture, the provider lookup and the clinic zone the report mapper reads. */
    static final Set<String> REQUIRED = required();

    private CdsExportHospitalReportSources() {}

    private static Set<String> required() {
        var result = new HashSet<>(ROWS);
        result.add(FILES);
        result.add("provider");
        result.add("clinical_timezone");
        return Set.copyOf(result);
    }
}
