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

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dto.CdsExportNoteIssueLinkDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Complete physical note/issue links; parent-note authorization covers this relation, not issue payloads. */
@Service
public class CdsExportNoteIssueLinkSourceReader implements CdsExportNativeSourceReader {
    private final CaseManagementNoteDAO source;
    public CdsExportNoteIssueLinkSourceReader(CaseManagementNoteDAO source) { this.source = java.util.Objects.requireNonNull(source); }
    @Override public String table() { return "casemgmt_issue_notes"; }
    @Override public Set<String> columns() { return Set.of("id", "note_id"); }
    @Override public Set<CdsCategory> categories() { return CdsExportNoteSourceReader.CATEGORIES; }
    @Override public Set<String> requiredTables() { return Set.of("casemgmt_note"); }
    @Override public CdsExportTableSchemaDto schema() { return source.readCdsExportIssueLinkSchema(); }
    @Override public java.util.List<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column> receiptColumns() { return source.readCdsExportIssueLinkReceiptColumns(); }
    @Override public long count(int patient) { return source.countCdsExportIssueLinks(patient); }
    @Override public void capture(int patient, CdsExportNativeCaptureComposer.Writer output) throws IOException {
        CdsExportNoteIssueLinkDto after = null;
        for (; ; ) {
            var page = source.findCdsExportIssueLinksAfter(patient, after, 32);
            if (page.size() > 32) invalid();
            for (var row : page) {
                if (row == null || after != null && key(row).compareTo(key(after)) <= 0) invalid();
                output.row(key(row), fields -> { fields.text("id", Long.toString(row.patientIssueId())); fields.text("note_id", Long.toString(row.noteId())); });
                after = row;
            }
            if (page.size() < 32) return;
        }
    }
    private static String key(CdsExportNoteIssueLinkDto row) { return CdsExportPatientTableSourceReader.key(row.noteId()) + CdsExportPatientTableSourceReader.key(row.patientIssueId()); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_NOTE_ISSUE_LINK_PAGE_INVALID"); }
}
