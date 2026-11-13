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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDocumentRepresentation.*;
import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.documentManager.DocumentLink;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Documents shaped as EDocUtil, AddEditHtml2Action and EDocUtil.deleteDocument store them. */
@Tag("unit")
@Tag("cds-export")
class CdsExportDocumentRepresentationUnitTest {
    private static Map<String, String> document(String status, String filename, String html) {
        var row = new HashMap<String, String>();
        row.put("status", status);
        row.put("docfilename", filename);
        row.put("docxml", html);
        return row;
    }

    private static Map<String, String> link(String status) {
        var row = new HashMap<String, String>();
        row.put("status", status);
        return row;
    }

    @Test
    void shouldExportTheFile_forAScannedOrUploadedDocument() {
        assertThat(of(document("A", "20260305101400scan.pdf", ""), link("A"))).isEqualTo(CdsExportDocumentRepresentation.FILE);
        // Legacy rows can hold NULL rather than empty docxml and a NULL link status.
        assertThat(of(document("A", "20260305101400scan.pdf", null), link(null))).isEqualTo(CdsExportDocumentRepresentation.FILE);
    }

    @Test
    void shouldExportTheStoredHtml_forAddHtmlAndAddLinkDocuments() {
        assertThat(of(document("H", "20260305101500html", "<p>Summary</p>"), link("H")))
                .isEqualTo(STORED_HTML);
        String redirect = DocumentLink.toRedirectHtml("https://portal.example.org/r?id=1");
        assertThat(of(document("H", "20260305101500link", redirect), link("H")))
                .isEqualTo(STORED_HTML);
        // The chart renders a non-empty docxml even when the row is otherwise an upload.
        assertThat(of(document("A", "20260305101400scan.pdf", "<p>Legacy</p>"), link("A")))
                .isEqualTo(STORED_HTML);
    }

    @Test
    void shouldLeaveOut_aSoftDeletedDocumentOrADeletedLink() {
        assertThat(of(document("D", "20260305101400scan.pdf", ""), link("A"))).isEqualTo(REMOVED);
        assertThat(of(document("D", "20260305101500html", "<p>Gone</p>"), link("H")))
                .isEqualTo(REMOVED);
        assertThat(of(document("A", "20260305101400scan.pdf", ""), link("D"))).isEqualTo(REMOVED);
    }

    @Test
    void shouldHoldForReview_whatTheChartCannotShowOrThisReleaseDoesNotKnow() {
        // An HTML document whose stored HTML is empty: the chart reports a missing file.
        assertThat(of(document("H", "20260305101500html", ""), link("H")))
                .isEqualTo(UNREPRESENTED);
        assertThat(of(document("I", "20260305101400scan.pdf", ""), link("A")))
                .isEqualTo(UNREPRESENTED);
        // Status comparison is exact, as the capture predicate's byte comparison is.
        assertThat(of(document("a", "20260305101400scan.pdf", ""), link("A")))
                .isEqualTo(UNREPRESENTED);
    }

    @Test
    void shouldAccountEveryCapturedFieldOfARemovedDocument_asNotExported() {
        var fields = new TreeMap<String, String>();
        Plan.DOCUMENTS.columns().forEach(column -> fields.put(column, "x"));
        fields.put("document_no", "91");
        fields.put("status", "D");
        var document =
                new CdsExportCapturedTextRowDto(
                        "document", CdsExportPatientTableSourceReader.key(91), fields);
        var linkRow =
                new CdsExportCapturedTextRowDto(
                        "ctl_document",
                        CdsExportPatientTableSourceReader.key(91),
                        Map.of(
                                "document_no", "91",
                                "module", "demographic",
                                "module_id", "27",
                                "status", "A"));
        var dispositions = removedDispositions(document, linkRow);
        assertThat(dispositions)
                .hasSize(Plan.DOCUMENTS.columns().size() + Plan.DOCUMENT_LINKS.columns().size());
        assertThat(dispositions.values())
                .allSatisfy(
                        assessment -> {
                            assertThat(assessment.outcome())
                                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
                            assertThat(assessment.rule()).isEqualTo(REMOVED_RULE);
                        });
    }
}
