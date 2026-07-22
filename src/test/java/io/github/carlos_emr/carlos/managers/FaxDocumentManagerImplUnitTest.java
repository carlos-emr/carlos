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
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Path;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FaxDocumentManagerImpl")
@Tag("unit")
@Tag("fast")
class FaxDocumentManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EformDataManager eformDataManager;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private FaxDocumentManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // ConvertToEdoc resolves NioFileManager in a static-final initializer; register the mock
        // BEFORE anything touches that class or its static init fails under instrumentation.
        registerMock(NioFileManager.class, mock(NioFileManager.class));
        manager = new FaxDocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "eformDataManager", eformDataManager);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should return the created eForm PDF path for fax rendering")
    void shouldReturnCreatedEformPdfPath_whenPdfGenerationSucceeds() throws Exception {
        Path expectedPath = Path.of("/tmp/fax-eform.pdf");
        when(eformDataManager.createEformPDF(loggedInInfo, 77)).thenReturn(expectedPath);

        Path actualPath = manager.getEformFaxDocument(loggedInInfo, 77);

        assertThat(actualPath).isEqualTo(expectedPath);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null);
        verify(eformDataManager).createEformPDF(loggedInInfo, 77);
    }

    @Test
    @DisplayName("should propagate the render failure when eForm PDF generation fails")
    void shouldPropagatePdfGenerationException_whenEformPdfGenerationFails() throws Exception {
        when(eformDataManager.createEformPDF(loggedInInfo, 77))
                .thenThrow(new PDFGenerationException("boom"));

        // Swallowing this and returning null used to detonate later as a context-free
        // NullPointerException in consumers opening the returned path.
        assertThatThrownBy(() -> manager.getEformFaxDocument(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessage("boom");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null);
        verify(eformDataManager).createEformPDF(loggedInInfo, 77);
    }

    @Test
    @DisplayName("should throw with the form name when form-to-PDF conversion produces no document")
    void shouldThrowPdfGenerationException_whenFormConversionReturnsNull() {
        FormTransportContainer formTransportContainer = mock(FormTransportContainer.class);
        when(formTransportContainer.getFormName()).thenReturn("BCAR");

        try (MockedStatic<ConvertToEdoc> convertToEdoc = Mockito.mockStatic(ConvertToEdoc.class)) {
            convertToEdoc.when(() -> ConvertToEdoc.saveAsTempPDF(formTransportContainer)).thenReturn(null);

            assertThatThrownBy(() -> manager.getFormFaxDocument(loggedInInfo, formTransportContainer))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessageContaining("BCAR");
        }
    }

    @Test
    @DisplayName("should return the converted form PDF path when conversion succeeds")
    void shouldReturnConvertedFormPdfPath_whenFormConversionSucceeds() throws Exception {
        FormTransportContainer formTransportContainer = mock(FormTransportContainer.class);
        when(formTransportContainer.getFormName()).thenReturn("BCAR");
        Path expectedPath = Path.of("/tmp/fax-form.pdf");

        try (MockedStatic<ConvertToEdoc> convertToEdoc = Mockito.mockStatic(ConvertToEdoc.class)) {
            convertToEdoc.when(() -> ConvertToEdoc.saveAsTempPDF(formTransportContainer)).thenReturn(expectedPath);

            Path actualPath = manager.getFormFaxDocument(loggedInInfo, formTransportContainer);

            assertThat(actualPath).isEqualTo(expectedPath);
        }
    }
}
