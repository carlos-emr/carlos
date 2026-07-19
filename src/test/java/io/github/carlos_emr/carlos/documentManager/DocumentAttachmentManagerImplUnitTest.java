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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@Tag("fast")
@DisplayName("DocumentAttachmentManagerImpl single eForm fax handling")
class DocumentAttachmentManagerImplUnitTest extends CarlosUnitTestBase {

    @Spy
    private DocumentAttachmentManagerImpl manager;

    @Test
    @DisplayName("should preserve the original eForm PDF when no attachments are present")
    void shouldPreserveOriginalEformPdf_whenNoAttachmentsPresent() throws PDFGenerationException {
        Path eformPdf = Path.of("/tmp/eform-browser-render.pdf");
        List<Object> pdfDocumentList = List.of(eformPdf.toString());

        Path result = manager.preserveSingleEformPdfWhenUnattached(eformPdf, pdfDocumentList);

        assertThat(result).isEqualTo(eformPdf);
        verify(manager, never()).concatPDF(org.mockito.ArgumentMatchers.<ArrayList<Object>>any());
    }

    @Test
    @DisplayName("should concatenate when additional documents are attached")
    void shouldConcatenate_whenAdditionalDocumentsExist() throws PDFGenerationException {
        Path eformPdf = Path.of("/tmp/eform-browser-render.pdf");
        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(eformPdf.toString());
        pdfDocumentList.add("/tmp/attachment.pdf");
        Path combinedPdf = Path.of("/tmp/combined.pdf");
        doReturn(combinedPdf).when(manager).concatPDF(pdfDocumentList);

        Path result = manager.preserveSingleEformPdfWhenUnattached(eformPdf, pdfDocumentList);

        assertThat(result).isEqualTo(combinedPdf);
        verify(manager).concatPDF(pdfDocumentList);
    }
}
