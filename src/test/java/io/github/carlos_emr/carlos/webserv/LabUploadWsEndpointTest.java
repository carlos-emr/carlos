/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * SOAP-level endpoint tests for {@link LabUploadWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for lab upload operations:
 * SOAP envelope marshalling/unmarshalling, WSDL processing, and response
 * serialization. Due to the heavy use of static helpers ({@link CarlosProperties},
 * {@link FileUploadCheck}, {@link HandlerClassFactory}, {@link Utilities}), these
 * tests use {@link MockedStatic} to isolate file I/O and database operations.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("LabUploadWs SOAP endpoint tests")
class LabUploadWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private CarlosProperties carlosProperties;

    @Mock
    private MessageHandler messageHandler;

    private MockedStatic<CarlosProperties> carlosPropertiesMock;
    private MockedStatic<FileUploadCheck> fileUploadCheckMock;
    private MockedStatic<HandlerClassFactory> handlerClassFactoryMock;
    private MockedStatic<Utilities> utilitiesMock;

    private LabUploadWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new LabUploadWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return LabUploadWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        fileUploadCheckMock = mockStatic(FileUploadCheck.class);
        handlerClassFactoryMock = mockStatic(HandlerClassFactory.class);
        utilitiesMock = mockStatic(Utilities.class);

        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(System.getProperty("java.io.tmpdir") + "/");
    }

    @AfterEach
    void tearDownStaticMocks() {
        if (utilitiesMock != null) utilitiesMock.close();
        if (handlerClassFactoryMock != null) handlerClassFactoryMock.close();
        if (fileUploadCheckMock != null) fileUploadCheckMock.close();
        if (carlosPropertiesMock != null) carlosPropertiesMock.close();
    }

    /** Tests for the uploadCLS SOAP operation. */
    @Nested
    @DisplayName("uploadCLS operation")
    class UploadCLS {

        @Test
        @DisplayName("should return success JSON when lab upload succeeds")
        void shouldReturnSuccessJson_whenLabUploadSucceeds() {
            fileUploadCheckMock.when(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), anyString()))
                .thenReturn(1);
            handlerClassFactoryMock.when(() -> HandlerClassFactory.getHandler("CLS"))
                .thenReturn(messageHandler);
            when(messageHandler.parse(any(LoggedInInfo.class), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("audit-info");

            LabUploadWs proxy = createClient(LabUploadWs.class);
            String result = proxy.uploadCLS("test_lab.hl7", "MSH|content", "999");

            assertThat(result).contains("\"success\":1");
            assertThat(result).contains("audit-info");
        }

        @Test
        @DisplayName("should return failure JSON when filename contains path traversal")
        void shouldReturnFailureJson_whenFilenameContainsPathTraversal() {
            LabUploadWs proxy = createClient(LabUploadWs.class);
            String result = proxy.uploadCLS("../etc/passwd", "content", "999");

            assertThat(result).contains("\"success\":0");
        }

        @Test
        @DisplayName("should return failure JSON when filename is empty")
        void shouldReturnFailureJson_whenFilenameIsEmpty() {
            LabUploadWs proxy = createClient(LabUploadWs.class);
            String result = proxy.uploadCLS("", "content", "999");

            assertThat(result).contains("\"success\":0");
        }
    }

    /** Tests for the uploadPDF SOAP operation. */
    @Nested
    @DisplayName("uploadPDF operation")
    class UploadPDF {

        @Test
        @DisplayName("should return success JSON when PDF upload succeeds")
        void shouldReturnSuccessJson_whenPdfUploadSucceeds() {
            utilitiesMock.when(() -> Utilities.savePdfFile(any(InputStream.class), anyString()))
                .thenReturn("/tmp/test.pdf");
            handlerClassFactoryMock.when(() -> HandlerClassFactory.getHandler("PDFDOC"))
                .thenReturn(messageHandler);
            when(messageHandler.parse(any(LoggedInInfo.class), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn("{\"success\":1,\"message\":\"\"}");

            LabUploadWs proxy = createClient(LabUploadWs.class);
            String result = proxy.uploadPDF("report.pdf", "PDF-content".getBytes(), "999");

            assertThat(result).contains("\"success\":1");
        }
    }
}
