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

import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPDFCreator.Omission;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The request-free consultation letter prints from explicit values only (no request, session,
 * Spring bean or file), and names a requested signature or logo it could not include instead of
 * leaving it out silently.
 */
@DisplayName("ConsultationPDFCreator request-free letter")
@Tag("unit")
class ConsultationPDFCreatorLetterUnitTest {

    private static final byte[] SIGNATURE = ConsultationPDFCreatorRequestPathUnitTest.png(300, 90, Color.BLUE);
    private static final byte[] LOGO = ConsultationPDFCreatorRequestPathUnitTest.png(400, 120, Color.RED);

    private static ConsultationLetterDto letter(boolean signatureRequested, byte[] signature,
            boolean logoRequested, byte[] logo) {
        return new ConsultationLetterDto(
                Locale.ENGLISH,
                new ConsultationLetterDto.Letterhead("Synthetic Clinic", "", "", "", "Synthetic Clinic",
                        "1 Test Street", "Testville", "ON", "A1A 1A1", "555-0100", "555-0101"),
                new ConsultationLetterDto.Request("0", "2026-01-15", "1", "Synthetic Service",
                        "Please reply with an appointment", true, false),
                new ConsultationLetterDto.Specialist("Specialist, Sam MD", "555-0400", "555-0401", "7 Consult Way"),
                new ConsultationLetterDto.Patient("SYNTHETIC, PATIENT", "5 Sample Lane", "555-0300", "", "",
                        "", "2001-02-03", "F", "ON", "0000000000", "AB", "2026-02-01", "3", "05", "PM", "C-42"),
                new ConsultationLetterDto.Clinical("Synthetic reason", "Clinical information", "", "",
                        "Allergy one", "", ""),
                new ConsultationLetterDto.Footer(true, "DOCTOR, REFERRING", "1234567", true, "DOCTOR, FAMILY", ""),
                new ConsultationLetterDto.Images(signatureRequested, signature, logoRequested, null, logo));
    }

    private record Printed(Set<Omission> omissions, String text, int images) {}

    private static Printed print(ConsultationLetterDto letter) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<Omission> omissions = new ConsultationPDFCreator(letter, output).printLetter();
        try (PDDocument document = Loader.loadPDF(output.toByteArray())) {
            int images = 0;
            for (var page : document.getPages())
                for (var name : page.getResources().getXObjectNames()) images++;
            return new Printed(omissions, new PDFTextStripper().getText(document), images);
        }
    }

    @Test
    @DisplayName("prints every supplied value with the signature and logo and reports nothing missing")
    void shouldPrintCompleteLetter_withoutRequestOrLookups() throws Exception {
        Printed printed = print(letter(true, SIGNATURE, true, LOGO));

        assertThat(printed.omissions()).isEmpty();
        assertThat(printed.images()).isEqualTo(2);
        assertThat(printed.text())
                .contains("Synthetic Clinic", "1 Test StreetTestville, ON. A1A 1A1", "Consultation Request",
                        "Date: 2026-01-15", "Please reply with an appointment", "Specialist, Sam MD", "Urgent",
                        "Synthetic Service", "SYNTHETIC, PATIENT", "(ON) 0000000000 AB", "Time: 3:05 PM",
                        "Synthetic reason", "Clinical information", "Allergy one", "Signature:",
                        "DOCTOR, REFERRING", "(1234567)", "MRP: DOCTOR, FAMILY");
    }

    @Test
    @DisplayName("reports a requested signature whose image is missing and prints no signature")
    void shouldReportSignature_whenRequestedSignatureIsMissing() throws Exception {
        Printed printed = print(letter(true, null, false, null));

        assertThat(printed.omissions()).containsExactly(Omission.SIGNATURE);
        assertThat(printed.images()).isZero();
        assertThat(printed.text()).doesNotContain("Signature:");
    }

    @Test
    @DisplayName("reports a requested signature whose bytes are not an image")
    void shouldReportSignature_whenSignatureIsNotAnImage() throws Exception {
        Printed printed = print(letter(true, "not an image".getBytes(StandardCharsets.US_ASCII), false, null));

        assertThat(printed.omissions()).containsExactly(Omission.SIGNATURE);
        assertThat(printed.images()).isZero();
    }

    @Test
    @DisplayName("reports a requested logo that is missing or not an image")
    void shouldReportLogo_whenRequestedLogoCannotBePrinted() throws Exception {
        assertThat(print(letter(false, null, true, null)).omissions()).containsExactly(Omission.LOGO);
        Printed garbled = print(letter(false, null, true, new byte[] {1, 2, 3}));

        assertThat(garbled.omissions()).containsExactly(Omission.LOGO);
        assertThat(garbled.images()).isZero();
    }

    @Test
    @DisplayName("prints no signature or logo and reports nothing when neither is requested")
    void shouldReportNothing_whenNeitherImageIsRequested() throws Exception {
        Printed printed = print(letter(false, SIGNATURE, false, LOGO));

        assertThat(printed.omissions()).isEmpty();
        assertThat(printed.images()).isZero();
    }

    @Test
    @DisplayName("exposes the reported omissions read-only")
    void shouldExposeReadOnlyOmissions_toCallers() throws Exception {
        Set<Omission> omissions = print(letter(true, null, false, null)).omissions();

        assertThatThrownBy(() -> omissions.add(Omission.LOGO)).isInstanceOf(UnsupportedOperationException.class);
    }
}
