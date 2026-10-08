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
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;

@Tag("unit")
@Tag("fast")
@DisplayName("ConsultAttachmentWarning")
class ConsultAttachmentWarningUnitTest {

    @Test
    @DisplayName("should name an attachment by type and id only, and key it for the fax confirmation")
    void shouldNameByTypeAndId_whenBuilt() {
        ConsultAttachmentWarning document = ConsultAttachmentWarning.unavailable(DocumentType.DOC, 41);
        ConsultAttachmentWarning lab = ConsultAttachmentWarning.notRendered(DocumentType.LAB, "7");

        assertThat(document.getKey()).isEqualTo("D:41");
        assertThat(document.isUnavailable()).isTrue();
        assertThat(document.getTypeLabelKey()).isEqualTo("encounter.oscarConsultationRequest.attachmentType.document");
        assertThat(lab.getKey()).isEqualTo("L:7");
        assertThat(lab.getReason()).isEqualTo(ConsultAttachmentWarning.Reason.NOT_RENDERED);
        assertThat(lab.getMessageKey()).isEqualTo("encounter.oscarConsultationRequest.attachmentWarning.notRendered");
    }

    @Test
    @DisplayName("should show anything but a plain id as \"?\", so no other text reaches a page or a log")
    void shouldHideId_whenItIsNotPlain() {
        assertThat(ConsultAttachmentWarning.unavailable(DocumentType.DOC, null).getId()).isEqualTo("?");
        assertThat(ConsultAttachmentWarning.unavailable(DocumentType.DOC, "../FAKE-Smith.pdf").getId()).isEqualTo("?");
        assertThat(ConsultAttachmentWarning.unavailable(DocumentType.DOC, "<script>").getId()).isEqualTo("?");
        assertThat(ConsultAttachmentWarning.notRendered(null, "x").getKey()).isEqualTo("?:x");
        assertThat(ConsultAttachmentWarning.notRendered(null, "x").getTypeLabelKey())
                .isEqualTo("encounter.oscarConsultationRequest.attachmentType.unknown");
    }

    @Test
    @DisplayName("should word each warning from the bundle in the reader's language")
    void shouldFormatFromBundle_inEnglishAndFrench() {
        ConsultAttachmentWarning document = ConsultAttachmentWarning.unavailable(DocumentType.DOC, 41);
        ConsultAttachmentWarning eForm = ConsultAttachmentWarning.notRendered(DocumentType.EFORM, 12);

        assertThat(document.format(Locale.ENGLISH))
                .isEqualTo("Document 41 is no longer available (it was deleted or does not belong to this patient).");
        assertThat(eForm.format(Locale.ENGLISH)).isEqualTo("eForm 12 could not be read. Printing and faxing "
                + "will not go ahead until it is fixed or detached.");
        assertThat(document.format(Locale.FRENCH))
                .isEqualTo("Document 41 : n’est plus disponible (élément supprimé ou n’appartenant pas à ce patient).");
        assertThat(ConsultAttachmentWarning.formatNames(List.of(document, eForm), Locale.ENGLISH))
                .isEqualTo("Document 41, eForm 12");
        assertThat(ConsultAttachmentWarning.formatNames(List.of(document, eForm), Locale.forLanguageTag("pl")))
                .isEqualTo("Dokument 41, eFormularz 12");
    }

    @Test
    @DisplayName("should treat an HRM report whose file is missing as unavailable, with its own wording")
    void shouldWordMissingFile_asUnavailable() {
        ConsultAttachmentWarning report = ConsultAttachmentWarning.fileUnavailable(DocumentType.HRM, 12);

        assertThat(report.isUnavailable()).isTrue();
        assertThat(report.getKey()).isEqualTo("H:12");
        assertThat(report.format(Locale.ENGLISH))
                .isEqualTo("HRM report 12 is no longer available (its file is missing or cannot be read).");
        assertThat(ConsultAttachmentWarning.notRenderedOnly(List.of(report))).isEmpty();
    }

    @Test
    @DisplayName("should read only warnings from the request attribute, and nothing when it is absent")
    void shouldReadWarnings_fromRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ConsultAttachmentWarning document = ConsultAttachmentWarning.unavailable(DocumentType.DOC, 41);
        ConsultAttachmentWarning lab = ConsultAttachmentWarning.notRendered(DocumentType.LAB, 7);

        assertThat(ConsultAttachmentWarning.fromRequest(request)).isEmpty();
        request.setAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE, List.of(document, "stray text", lab));

        assertThat(ConsultAttachmentWarning.fromRequest(request)).containsExactly(document, lab);
        assertThat(ConsultAttachmentWarning.notRenderedOnly(ConsultAttachmentWarning.fromRequest(request)))
                .containsExactly(lab);
    }

    @Test
    @DisplayName("should give the key itself when the bundle has no such key")
    void shouldFallBackToKey_whenBundleLacksIt() {
        assertThat(ConsultAttachmentWarning.bundleText(Locale.ENGLISH, "no.such.key.for.this.test"))
                .isEqualTo("no.such.key.for.this.test");
    }
}
