/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.fax.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;

/**
 * Unit tests for {@link PdfCoverPageCreator}.
 *
 * <p>Tests PDF fax cover page generation including output validity
 * and non-empty content.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("PdfCoverPageCreator")
class PdfCoverPageCreatorUnitTest {

    private static final String SAMPLE_NOTE = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
            + "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
            + "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.";

    @Test
    @DisplayName("should create non-empty PDF cover page")
    void shouldCreateNonEmptyPdf_coverPage() throws Exception {
        FaxRecipient recipient = new FaxRecipient("Test Recipient Clinic", "778-998-0876");
        FaxAccount sender = new FaxAccount();
        sender.setName("Dr. Test Sender");
        sender.setFax("604-555-1234");

        PdfCoverPageCreator creator = new PdfCoverPageCreator(
                SAMPLE_NOTE, 5, recipient, sender);

        byte[] pdf = creator.createCoverPage();

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("should produce valid PDF header")
    void shouldProduceValidPdfHeader() throws Exception {
        FaxRecipient recipient = new FaxRecipient("Clinic", "555-1234");
        FaxAccount sender = new FaxAccount();
        sender.setName("Sender");
        sender.setFax("555-5678");

        PdfCoverPageCreator creator = new PdfCoverPageCreator(
                "Short note", 1, recipient, sender);

        byte[] pdf = creator.createCoverPage();

        // PDF files start with %PDF
        assertThat(pdf.length).isGreaterThan(4);
        String header = new String(pdf, 0, 4);
        assertThat(header).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("should handle empty note")
    void shouldHandleEmptyNote() throws Exception {
        FaxRecipient recipient = new FaxRecipient("Clinic", "555-1234");
        FaxAccount sender = new FaxAccount();
        sender.setName("Sender");
        sender.setFax("555-5678");

        PdfCoverPageCreator creator = new PdfCoverPageCreator(
                "", 1, recipient, sender);

        byte[] pdf = creator.createCoverPage();

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
    }
}
