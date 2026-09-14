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

        Path result = manager.preserveSingleEformPdfWhenUnattached(eformPdf, pdfDocumentList, "1234");

        assertThat(result).isEqualTo(eformPdf);
        verify(manager, never()).concatPDF(
                org.mockito.ArgumentMatchers.<ArrayList<Object>>any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("should concatenate when additional documents are attached")
    void shouldConcatenate_whenAdditionalDocumentsExist() throws PDFGenerationException {
        Path eformPdf = Path.of("/tmp/eform-browser-render.pdf");
        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(eformPdf.toString());
        pdfDocumentList.add("/tmp/attachment.pdf");
        Path combinedPdf = Path.of("/tmp/combined.pdf");
        doReturn(combinedPdf).when(manager).concatPDF(pdfDocumentList, "1234");

        Path result = manager.preserveSingleEformPdfWhenUnattached(eformPdf, pdfDocumentList, "1234");

        assertThat(result).isEqualTo(combinedPdf);
        // The demographic must reach concatPDF: it scopes the packet filename, which becomes the
        // stored document name after promotion.
        verify(manager).concatPDF(pdfDocumentList, "1234");
    }

    @Test
    @DisplayName("should scope the merged packet filename to the patient it belongs to")
    void shouldScopePacketFilename_toDemographic(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        // The packet's temp filename is not cosmetic: promoteApplicationTempFile promotes by
        // basename, so this name becomes the stored document name in DOCUMENT_DIR. Naming every
        // packet combinedPDF_<epochMillis> made it patient-agnostic, so two packets built in the same
        // millisecond — different patients, different specialists — competed for one destination.
        // Promotion now claims destinations atomically, but a name that cannot collide across
        // patients keeps a same-millisecond collision inside one chart, where the worst case is a
        // duplicate rather than a cross-patient disclosure.
        Path input = tempDir.resolve("packet-input.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(input.toFile());
        }
        Path merged = tempDir.resolve("merged.pdf");
        java.nio.file.Files.copy(input, merged);

        io.github.carlos_emr.carlos.managers.NioFileManager fileManager =
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.NioFileManager.class);
        doReturn(merged).when(fileManager)
                .saveTempFile(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.io.ByteArrayOutputStream.class));
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "nioFileManager", fileManager);

        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(input.toString());

        manager.concatPDF(pdfDocumentList, "4242");

        org.mockito.ArgumentCaptor<String> packetName = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fileManager).saveTempFile(packetName.capture(),
                org.mockito.ArgumentMatchers.any(java.io.ByteArrayOutputStream.class));
        assertThat(packetName.getValue())
                .describedAs("the packet name must identify its patient")
                .startsWith("combinedPDF_4242_");
    }

    @Test
    @DisplayName("should fall back to an unscoped packet filename when the demographic is unusable")
    void shouldFallBackToUnscopedName_whenDemographicNotNumeric(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        // demographicId arrives as a request attribute. A malformed one must degrade to the previous
        // naming rather than being interpolated into a filename, so the digits-only guard is the
        // thing under test here — not merely that some name is produced.
        Path input = tempDir.resolve("packet-input.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(input.toFile());
        }
        Path merged = tempDir.resolve("merged.pdf");
        java.nio.file.Files.copy(input, merged);

        io.github.carlos_emr.carlos.managers.NioFileManager fileManager =
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.NioFileManager.class);
        doReturn(merged).when(fileManager)
                .saveTempFile(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.io.ByteArrayOutputStream.class));
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "nioFileManager", fileManager);

        ArrayList<Object> pdfDocumentList = new ArrayList<>();
        pdfDocumentList.add(input.toString());

        manager.concatPDF(pdfDocumentList, "../../etc/passwd");

        org.mockito.ArgumentCaptor<String> packetName = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fileManager).saveTempFile(packetName.capture(),
                org.mockito.ArgumentMatchers.any(java.io.ByteArrayOutputStream.class));
        assertThat(packetName.getValue())
                .describedAs("a non-numeric demographic must never reach the filename")
                .startsWith("combinedPDF_")
                .doesNotContain("etc")
                .doesNotContain("..");
    }

    @Test
    @DisplayName("should leave the PDF file byte-identical when flattening a form with no AcroForm")
    void shouldLeavePdfBytesUntouched_whenFlatteningWithNoAcroForm(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        // Regression pin for the merged-PDF font corruption: flattenPDFFormFields used to call
        // document.save() onto the SAME file the open PDDocument was still lazily reading from.
        // PDFBox streams objects (embedded font programs included) from the backing file during
        // save, so overwriting it mid-save self-clobbered those streams — the browser-rendered
        // eForm page of every merged PDF lost its embedded subset font and extracted as
        // glyph-shifted garbage. With no AcroForm there is nothing to flatten, so the file must
        // not be rewritten at all.
        Path pdf = tempDir.resolve("eform-browser-render-flatten.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(pdf.toFile());
        }
        byte[] before = java.nio.file.Files.readAllBytes(pdf);

        manager.flattenPDFFormFields(pdf);

        byte[] after = java.nio.file.Files.readAllBytes(pdf);
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("should flatten form fields into a valid PDF when an AcroForm is present")
    void shouldFlattenFieldsIntoValidPdf_whenAcroFormPresent(@org.junit.jupiter.api.io.TempDir Path tempDir) throws Exception {
        // The WITH-AcroForm branch runs on every merged fax/eDoc packet that carries a fillable
        // attachment. It has no other coverage, and a regression here (e.g. reverting to
        // document.save(pdfPath) onto the live backing file, or a truncated write) would ship a
        // corrupted clinical PDF to a fax recipient while the workflow reports success. Build a real
        // one-field AcroForm with a sentinel value, flatten it, and assert the output re-loads, keeps
        // its single page, has no interactive fields left, and carries the value as flattened content.
        Path pdf = tempDir.resolve("acroform-flatten.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm acroForm =
                    new org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(acroForm);
            org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
            resources.put(org.apache.pdfbox.cos.COSName.getPDFName("Helv"),
                    new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA));
            acroForm.setDefaultResources(resources);
            org.apache.pdfbox.pdmodel.interactive.form.PDTextField field =
                    new org.apache.pdfbox.pdmodel.interactive.form.PDTextField(acroForm);
            field.setPartialName("patientNote");
            field.setDefaultAppearance("/Helv 12 Tf 0 g");
            acroForm.getFields().add(field);
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget = field.getWidgets().get(0);
            widget.setRectangle(new org.apache.pdfbox.pdmodel.common.PDRectangle(50, 700, 300, 20));
            widget.setPage(page);
            page.getAnnotations().add(widget);
            field.setValue("FLATTEN-SENTINEL");
            document.save(pdf.toFile());
        }

        manager.flattenPDFFormFields(pdf);

        try (org.apache.pdfbox.pdmodel.PDDocument flattened = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm remaining =
                    flattened.getDocumentCatalog().getAcroForm();
            assertThat(remaining == null || remaining.getFields().isEmpty()).isTrue();
            assertThat(flattened.getNumberOfPages()).isEqualTo(1);
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(flattened)).contains("FLATTEN-SENTINEL");
        }
    }
}
