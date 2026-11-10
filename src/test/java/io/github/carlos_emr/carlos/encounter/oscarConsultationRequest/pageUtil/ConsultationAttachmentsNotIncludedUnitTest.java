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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Generic consultation omission warnings and their rendered PDF notices.
 *
 * @since 2026-11-04
 */
@Tag("unit")
class ConsultationAttachmentsNotIncludedUnitTest extends CarlosUnitTestBase {
    private final ConsultationManager manager = mock(ConsultationManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);

    @Test
    void shouldWarnWithoutExposingReports_whenFormsAndHrmsAreOmitted() throws Exception {
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_FORM))
                .thenReturn(List.of(new ConsultDocs(42, 7001, ConsultDocs.DOCTYPE_FORM, "999998")));
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_HRM))
                .thenReturn(List.of(new ConsultDocs(42, 8001, ConsultDocs.DOCTYPE_HRM, "999998"),
                        new ConsultDocs(42, 8002, ConsultDocs.DOCTYPE_HRM, "999998")));
        List<String> warnings = ConsultationAttachmentsNotIncluded.describe(manager, info, 42,
                List.of(), List.of(), Locale.ENGLISH);
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0)).contains("clinical form");
        assertThat(warnings.get(1)).isEqualTo("An attachment is unavailable and has not been included.");
        try (var pdf = Loader.loadPDF(ConsultationAttachmentsNotIncluded.noticePage(warnings, Locale.ENGLISH))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Attachments not included", "contact the sender")
                    .doesNotContain("7001", "8001", "8002", "HRM", "report");
        }
        assertThat(ConsultationAttachmentsNotIncluded.previewWarning(warnings, Locale.ENGLISH))
                .contains("Attachments not included").doesNotContain("HRM", "8001", "8002");
    }

    @Test
    void shouldNotWarn_whenEveryStoredAttachmentIsListed() {
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_FORM))
                .thenReturn(List.of(new ConsultDocs(42, 7, ConsultDocs.DOCTYPE_FORM, "999998")));
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_HRM))
                .thenReturn(List.of(new ConsultDocs(42, 8, ConsultDocs.DOCTYPE_HRM, "999998")));
        assertThat(ConsultationAttachmentsNotIncluded.describe(manager, info, 42,
                List.of(new EctFormData.PatientForm("formAnnual", "Annual", 7, 1)),
                List.of(Map.of("id", 8)), Locale.ENGLISH)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt-BR", ""})
    void shouldPreserveWarnings_whenRelationshipsAreAlreadyFetched(String language) {
        List<ConsultDocs> storedForms = List.of(new ConsultDocs(42, 7, ConsultDocs.DOCTYPE_FORM, "999998"));
        List<ConsultDocs> storedHrms = List.of(new ConsultDocs(42, 8, ConsultDocs.DOCTYPE_HRM, "999998"));
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_FORM)).thenReturn(storedForms);
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_HRM)).thenReturn(storedHrms);
        Locale locale = language.isEmpty() ? null : Locale.forLanguageTag(language);
        for (boolean formAvailable : List.of(false, true)) {
            for (boolean hrmAvailable : List.of(false, true)) {
                List<EctFormData.PatientForm> forms = formAvailable
                        ? List.of(new EctFormData.PatientForm("formAnnual", "Annual", 7, 1)) : List.of();
                List<Map<String, ?>> hrms = hrmAvailable ? List.of(Map.of("id", 8)) : List.of();
                List<String> expected = ConsultationAttachmentsNotIncluded.describe(manager, info, 42, forms, hrms, locale);
                clearInvocations(manager);

                assertThat(ConsultationAttachmentsNotIncluded.describe(storedForms, storedHrms, forms, hrms, locale))
                        .isEqualTo(expected).hasSize((formAvailable ? 0 : 1) + (hrmAvailable ? 0 : 1));
                verifyNoInteractions(manager);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt-BR"})
    void shouldRenderBundleNotice_whenLocaleIsSupported(String language) throws Exception {
        when(manager.getAttachedDocumentsByType(info, 42, ConsultDocs.DOCTYPE_HRM))
                .thenReturn(List.of(new ConsultDocs(42, 8, ConsultDocs.DOCTYPE_HRM, "999998")));
        Locale locale = Locale.forLanguageTag(language);
        List<String> warnings = ConsultationAttachmentsNotIncluded.describe(manager, info, 42, List.of(), List.of(), locale);
        try (var pdf = Loader.loadPDF(ConsultationAttachmentsNotIncluded.noticePage(warnings, locale))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains(warnings.get(0));
        }
    }
}
