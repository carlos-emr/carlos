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
package io.github.carlos_emr.carlos.documentManager.data;

import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.tickler.dto.TicklerLinkDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the request contract between the attachment picker and the tickler actions, and the
 * legacy viewer-code mapping the tickler list and REST clients still switch on.
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@Tag("tickler")
@DisplayName("Tickler attachment request contract")
class TicklerAttachmentParametersUnitTest {

    @Test
    @DisplayName("should read every picker parameter, trimming and de-duplicating")
    void shouldReadAllTypes_fromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("docNo", "11", " 11 ", "", "12");
        request.addParameter("labNo", "77");
        request.addParameter("attachmentsSubmitted", "1");

        Map<DocumentType, Set<String>> submitted = TicklerAttachmentParameters.read(request);

        assertThat(TicklerAttachmentParameters.isSubmitted(request)).isTrue();
        assertThat(submitted).containsOnlyKeys(DocumentType.values());
        assertThat(submitted.get(DocumentType.DOC)).containsExactly("11", "12");
        assertThat(submitted.get(DocumentType.LAB)).containsExactly("77");
        assertThat(submitted.get(DocumentType.EFORM)).isEmpty();
        assertThat(submitted.get(DocumentType.HRM)).isEmpty();
        assertThat(submitted.get(DocumentType.FORM)).isEmpty();
    }

    @Test
    @DisplayName("should not treat a submission without the marker as authoritative")
    void shouldReportNotSubmitted_whenMarkerMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("docNo", "11");

        assertThat(TicklerAttachmentParameters.isSubmitted(request)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"DOC,DOC", "doc,DOC", "HRM,HRM", "HL7,LAB", "MDS,LAB", "CML,LAB", "BCP,LAB"})
    @DisplayName("should map legacy forward-from-document codes to attachment types")
    void shouldMapLegacyDocType_toDocumentType(String legacy, DocumentType expected) {
        assertThat(TicklerAttachmentParameters.fromLegacyDocType(legacy)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should not guess an attachment type for blank or unknown legacy codes")
    void shouldReturnNull_forUnknownLegacyDocType() {
        assertThat(TicklerAttachmentParameters.fromLegacyDocType(null)).isNull();
        assertThat(TicklerAttachmentParameters.fromLegacyDocType("  ")).isNull();
        assertThat(TicklerAttachmentParameters.fromLegacyDocType("XYZ")).isNull();
    }

    @Test
    @DisplayName("should map ticklerdocs rows back to the legacy viewer codes")
    void shouldMapDocType_toLegacyTableName() {
        assertThat(TicklerLinkDTO.legacyTableName(row("D", null))).isEqualTo("DOC");
        assertThat(TicklerLinkDTO.legacyTableName(row("H", null))).isEqualTo("HRM");
        assertThat(TicklerLinkDTO.legacyTableName(row("E", null))).isEqualTo("EFORM");
        assertThat(TicklerLinkDTO.legacyTableName(row("F", null))).isEqualTo("FORM");
        assertThat(TicklerLinkDTO.legacyTableName(row("L", "MDS"))).isEqualTo("MDS");
        // A lab row without a recorded source defaults to the HL7 viewer.
        assertThat(TicklerLinkDTO.legacyTableName(row("L", null))).isEqualTo("HL7");
    }

    @Test
    @DisplayName("should carry id, tickler, type and lab source into the list DTO")
    void shouldBuildLinkDto_fromTicklerDocs() {
        TicklerDocs stored = row("L", "CML");
        stored.setId(9);

        TicklerLinkDTO dto = TicklerLinkDTO.fromTicklerDocs(stored);

        assertThat(dto.getId()).isEqualTo(9);
        assertThat(dto.getTicklerNo()).isEqualTo(42);
        assertThat(dto.getTableName()).isEqualTo("CML");
        assertThat(dto.getTableId()).isEqualTo(77L);
        assertThat(dto.getDocType()).isEqualTo("L");
        assertThat(dto.getLabType()).isEqualTo("CML");
    }

    private static TicklerDocs row(String docType, String labType) {
        TicklerDocs row = new TicklerDocs(42, 77, docType, "999998");
        row.setLabType(labType);
        return row;
    }
}
