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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Issue #4693: a consultation PDF cannot draw CML, MDS or PathNet (BCP) lab attachments, and it
 * used to leave them out without a word. These tests pin how such a lab is named to the sender and
 * the receiver, and the page that lists them in the packet.
 */
@DisplayName("ConsultationLabsNotIncluded")
@Tag("unit")
@Tag("fast")
class ConsultationLabsNotIncludedUnitTest {

    private static final String PLACEHOLDER_LABEL =
            "Lab attachment unavailable: select the lab again to confirm its source before printing";

    private static LabResultData listedLab(String type, String discipline, String dateTime) {
        LabResultData lab = mock(LabResultData.class);
        when(lab.getLabType()).thenReturn(type);
        when(lab.getDiscipline()).thenReturn(discipline);
        when(lab.getDateTime()).thenReturn(dateTime);
        return lab;
    }

    /** The row CommonLabResultData adds for an attachment no listing matched. */
    private static LabResultData unavailableLab(String type) {
        LabResultData lab = mock(LabResultData.class);
        when(lab.getLabType()).thenReturn(type);
        when(lab.isAttachmentUnavailable()).thenReturn(true);
        when(lab.getLabel()).thenReturn(PLACEHOLDER_LABEL);
        return lab;
    }

    private static String pdfText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Nested
    @DisplayName("describe")
    class Describe {

        @Test
        @DisplayName("should name an MDS lab by source, discipline and date")
        void shouldNameSourceDisciplineAndDate_forListedMdsLab() {
            List<String> names = ConsultationLabsNotIncluded.describe(
                    List.of(listedLab("MDS", "Hem/Chem/Other", "2024-03-05 10:22:00")), Locale.ENGLISH);

            assertThat(names).containsExactly("MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00");
        }

        @Test
        @DisplayName("should name the CML and PathNet sources of listed legacy labs")
        void shouldNameEachSource_forListedLegacyLabs() {
            List<String> names = ConsultationLabsNotIncluded.describe(List.of(
                    listedLab("CML", "Hematology", "05-Mar-24"),
                    listedLab("BCP", "Chemistry", "2024-03-05")), Locale.ENGLISH);

            assertThat(names).containsExactly(
                    "CML lab result: Hematology, 05-Mar-24",
                    "PathNet lab result: Chemistry, 2024-03-05");
        }

        @Test
        @DisplayName("should leave out a missing date rather than print an empty one")
        void shouldOmitDate_whenListedLabHasNoDate() {
            List<String> names = ConsultationLabsNotIncluded.describe(
                    List.of(listedLab("MDS", "Microbiology", "  ")), Locale.ENGLISH);

            assertThat(names).containsExactly("MDS lab result: Microbiology");
        }

        @Test
        @DisplayName("should name an unavailable attachment by source only, never by its placeholder label")
        void shouldSayDetailsUnavailable_forUnavailableLegacyAttachment() {
            List<String> names = ConsultationLabsNotIncluded.describe(List.of(unavailableLab("MDS")), Locale.ENGLISH);

            assertThat(names).containsExactly("MDS lab result (details unavailable)");
        }

        @Test
        @DisplayName("should not claim a source for an attachment whose source was never recorded")
        void shouldSaySourceNotConfirmed_forUnresolvedAttachment() {
            List<String> names = ConsultationLabsNotIncluded.describe(
                    List.of(unavailableLab("UNRESOLVED")), Locale.ENGLISH);

            assertThat(names).containsExactly("Lab result (source not confirmed)");
        }

        @Test
        @DisplayName("should not claim a source for a lab that carries no lab type at all")
        void shouldSaySourceNotConfirmed_whenLabTypeIsMissing() {
            List<String> names = ConsultationLabsNotIncluded.describe(
                    List.of(listedLab(null, "Hematology", "05-Mar-24")), Locale.ENGLISH);

            assertThat(names).containsExactly("Lab result (source not confirmed)");
        }

        @Test
        @DisplayName("should still name a lab, without details, when its discipline cannot be looked up")
        void shouldSayDetailsUnavailable_whenDisciplineLookupFails() {
            LabResultData cml = mock(LabResultData.class);
            when(cml.getLabType()).thenReturn("CML");
            when(cml.getDiscipline()).thenThrow(new IllegalStateException("no CML tables"));
            when(cml.getDateTime()).thenReturn("05-Mar-24");

            List<String> names = ConsultationLabsNotIncluded.describe(List.of(cml), Locale.ENGLISH);

            assertThat(names).containsExactly("CML lab result (details unavailable)");
        }

        @Test
        @DisplayName("should name the lab in the reader's language")
        void shouldNameInReadersLanguage_forFrenchLocale() {
            List<String> names = ConsultationLabsNotIncluded.describe(
                    List.of(listedLab("MDS", "Hem/Chem/Other", "2024-03-05 10:22:00")), Locale.FRENCH);

            // French puts a non-breaking space before the colon, as the bundle's other print labels do.
            assertThat(names).containsExactly("Résultat de laboratoire MDS\u00a0: Hem/Chem/Other, 2024-03-05 10:22:00");
        }

        @Test
        @DisplayName("should return an empty list when no lab is left out")
        void shouldReturnEmptyList_forNoLabs() {
            assertThat(ConsultationLabsNotIncluded.describe(List.of(), Locale.ENGLISH)).isEmpty();
            assertThat(ConsultationLabsNotIncluded.describe(null, Locale.ENGLISH)).isEmpty();
        }
    }

    @Nested
    @DisplayName("noticePage")
    class NoticePage {

        @Test
        @DisplayName("should list every name under a heading the receiver can act on, on one page")
        void shouldListEveryName_onNoticePage() throws Exception {
            byte[] pdf = ConsultationLabsNotIncluded.noticePage(List.of(
                    "MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00",
                    "Lab result (source not confirmed)"), Locale.ENGLISH);

            assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            assertThat(pageCount(pdf)).isEqualTo(1);
            String text = pdfText(pdf);
            assertThat(text).contains("Attachments not included");
            assertThat(text).contains("contact the sender");
            assertThat(text).contains("MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00");
            assertThat(text).contains("Lab result (source not confirmed)");
        }

        @Test
        @DisplayName("should draw the translated heading and non-Latin characters in the names")
        void shouldDrawTranslatedTextAndNonLatinNames_forPolishLocale() throws Exception {
            byte[] pdf = ConsultationLabsNotIncluded.noticePage(
                    List.of("Wynik laboratoryjny MDS: Łódź"), Locale.forLanguageTag("pl"));

            String text = pdfText(pdf);
            assertThat(text).contains("Załączniki nieuwzględnione");
            assertThat(text).contains("Wynik laboratoryjny MDS: Łódź");
        }

        @Test
        @DisplayName("should refuse to draw a notice that names nothing")
        void shouldRefuseEmptyList_forNoticePage() {
            assertThatThrownBy(() -> ConsultationLabsNotIncluded.noticePage(List.of(), Locale.ENGLISH))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("previewWarning")
    class PreviewWarning {

        @Test
        @DisplayName("should put each name on its own line after the explanation")
        void shouldListEachNameOnItsOwnLine_inPreviewWarning() {
            String warning = ConsultationLabsNotIncluded.previewWarning(Arrays.asList(
                    "MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00",
                    "Lab result (source not confirmed)"), Locale.ENGLISH);

            assertThat(warning).startsWith("The PDF was created, but these attached lab results could not be included in it:");
            assertThat(warning).contains("\n- MDS lab result: Hem/Chem/Other, 2024-03-05 10:22:00\n- Lab result (source not confirmed)\n");
        }
    }
}
