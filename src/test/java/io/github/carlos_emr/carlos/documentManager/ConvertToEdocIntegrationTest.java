/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Exercises real HTML-to-PDF fallback rendering, temporary storage and EDoc metadata.
 * Only the native converter and storage destination are replaced: this test does not
 * depend on native wkhtmltox availability or write into the configured document store.
 */
@Tag("integration")
@Tag("document")
@DisplayName("ConvertToEdoc Integration Tests")
class ConvertToEdocIntegrationTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Referral letter"})
    @DisplayName("should render readable PDF and preserve metadata for optional eForm subjects")
    void shouldRenderPdf_whenSubjectIsOptional(String subject) throws Exception {
        EFormData eform = new EFormData();
        eform.setFormId(100);
        eform.setDemographicId(17);
        eform.setProviderNo("999001");
        eform.setFormName("Test Form");
        eform.setSubject(subject);
        eform.setFormData("<html><body><p>Referral integration fixture</p></body></html>");
        NioFileManager fileManager = mock(NioFileManager.class);
        when(fileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class)))
                .thenAnswer(invocation -> {
                    Path destination = tempDir.resolve(invocation.getArgument(0, String.class));
                    Files.write(destination, invocation.getArgument(1, ByteArrayOutputStream.class).toByteArray());
                    return destination;
                });

        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedConstruction<InternalEDocConverter> nativeConverter = mockConstruction(
                     InternalEDocConverter.class, (converter, context) ->
                             doThrow(new IOException("Native renderer unavailable in fixture"))
                                     .when(converter).convert(anyString(), any()))) {
            spring.when(() -> SpringUtils.getBean(NioFileManager.class)).thenReturn(fileManager);
            EDoc result = ConvertToEdoc.from(eform);
            String expectedDescription = subject == null || subject.isBlank() ? "Test Form" : subject;
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEqualTo(expectedDescription);
            assertThat(result.getModuleId()).isEqualTo("17");
            assertThat(result.getNumberOfPages()).isEqualTo(1);
            Path pdfPath = Path.of(result.getFilePath()).resolve(result.getFileName());
            assertThat(pdfPath).isRegularFile();
            try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
                assertThat(pdf.getNumberOfPages()).isEqualTo(1);
                assertThat(new PDFTextStripper().getText(pdf)).contains("Referral integration fixture");
            }
            // The pre-rendered overload must apply the same optional-subject fallback.
            EDoc fromExistingPdf = ConvertToEdoc.from(eform, pdfPath);
            assertThat(fromExistingPdf.getDescription()).isEqualTo(expectedDescription);
            assertThat(fromExistingPdf.getNumberOfPages()).isEqualTo(1);
            verify(fileManager).saveTempFile(anyString(), any(ByteArrayOutputStream.class));
        }
    }
}
