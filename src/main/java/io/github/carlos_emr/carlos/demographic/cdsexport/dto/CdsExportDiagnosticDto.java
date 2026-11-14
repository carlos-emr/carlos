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

import java.util.List;

/**
 * Diagnostic report for an authorized observer of the job. No native row, clinical value,
 * clinical intent, stored member, key or grant is serialized. The only patient-correlating values
 * are the chart numbers of patients the current attempt held back, each with its safe reason
 * code, because the operator must know which charts to correct before exporting them. The report
 * names every held chart and every event of the job (newest first), whatever their number: it is
 * read page by page from the ledger, and nothing is cut.
 *
 * <p>{@code unlinkedSourceRows} counts, per reviewed inbox table, the rows the current attempt
 * found linked to no patient (an unmatched lab or HRM report, an incoming document not yet
 * filed): they are in no chart, so they held no patient and were not exported. Table names and
 * counts only.
 *
 * <p>{@code legacyLabReports} counts the legacy-format laboratory reports (MDS, CML, BC PathNet)
 * the current attempt wrote in Reports because the job selected LaboratoryResults without
 * Reports; a count only.
 */
public record CdsExportDiagnosticDto(int apiVersion, String event, String runId, String state, long epoch,
        Long totalPatients, long processedPatients, long blockedPatients, boolean cancelRequested,
        boolean complete, boolean artifactAvailable, String safeCode, String createdAt, String updatedAt,
        List<Audit> recentEvents, List<Held> heldPatients, List<Unlinked> unlinkedSourceRows,
        long legacyLabReports) {
    public CdsExportDiagnosticDto {
        recentEvents = List.copyOf(recentEvents);
        heldPatients = List.copyOf(heldPatients);
        unlinkedSourceRows = List.copyOf(unlinkedSourceRows);
        if (legacyLabReports < 0) throw new IllegalArgumentException("CDS_EXPORT_DIAGNOSTIC_INVALID");
    }

    /** Report shape without held-back patients, unlinked inbox counts or legacy reports. */
    public CdsExportDiagnosticDto(int apiVersion, String event, String runId, String state, long epoch,
            Long totalPatients, long processedPatients, long blockedPatients, boolean cancelRequested,
            boolean complete, boolean artifactAvailable, String safeCode, String createdAt, String updatedAt,
            List<Audit> recentEvents) {
        this(apiVersion, event, runId, state, epoch, totalPatients, processedPatients, blockedPatients,
                cancelRequested, complete, artifactAvailable, safeCode, createdAt, updatedAt, recentEvents,
                List.of(), List.of(), 0);
    }

    public record Audit(String type, long epoch, String safeCode, String at) { }

    /** Rows of one reviewed inbox table that named no patient, so held none and were not exported. */
    public record Unlinked(String table, long rows) { }

    /** One held-back chart and the safe code saying why it is not in the package. */
    public record Held(int patient, String safeCode) {
        @Override
        public String toString() {
            return "Held[protected]";
        }
    }
}
