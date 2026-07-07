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
package io.github.carlos_emr.carlos.app.security;

import io.github.carlos_emr.carlos.admin.web.ManageFlowsheetsUpload2Action;
import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action;
import io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.InsideLabUpload2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.web.ProviderSignatureStamp2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("upload action callback validation")
@Tag("unit")
@Tag("security")
class UploadActionCallbackValidationUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(MeasurementCSSLocationDao.class, mock(MeasurementCSSLocationDao.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("measurement stylesheet callback should capture valid upload metadata")
    void shouldCaptureMeasurementStylesheetUpload_whenCallbackReceivesValidFile() throws Exception {
        Path upload = Files.createTempFile(tempDir, "measurement-style", ".css");
        UploadedFile uploadedFile = uploadedFile(upload.toFile(), "style.css", "text/css");

        EctAddMeasurementStyleSheet2Action action = new EctAddMeasurementStyleSheet2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getFile()).isNotNull();
        assertThat(action.getFile().getCanonicalPath()).isEqualTo(upload.toFile().getCanonicalPath());
        assertThat(action.getFileName()).isEqualTo("style.css");
    }

    @Test
    @DisplayName("measurement stylesheet callback should convert invalid content to upload error")
    void shouldRejectMeasurementStylesheetUpload_whenContentIsNotFile() {
        UploadedFile uploadedFile = uploadedFile("not-a-file", "style.css", "text/css");

        EctAddMeasurementStyleSheet2Action action = new EctAddMeasurementStyleSheet2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getFile()).isNull();
        assertThat(action.getFileName()).isNull();
    }

    @Test
    @DisplayName("form XML callback should capture valid upload content type and filename")
    void shouldCaptureFormXmlUpload_whenCallbackReceivesValidFile() throws Exception {
        Path upload = Files.createTempFile(tempDir, "forms", ".zip");
        UploadedFile uploadedFile = uploadedFile(upload.toFile(), "forms.zip", "application/zip");

        FrmXmlUpload2Action action = new FrmXmlUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getFile1()).isNotNull();
        assertThat(action.getFile1().getCanonicalPath()).isEqualTo(upload.toFile().getCanonicalPath());
        assertThat(action.getFile1FileName()).isEqualTo("forms.zip");
        assertThat(action.getFile1ContentType()).isEqualTo("application/zip");
    }

    @Test
    @DisplayName("inside lab callback should retain valid files and flag invalid filenames")
    void shouldFlagInsideLabUpload_whenOneFilenameIsInvalid() throws Exception {
        Path validUpload = Files.createTempFile(tempDir, "lab-valid", ".hl7");
        Path hiddenUpload = Files.createTempFile(tempDir, "lab-hidden", ".hl7");

        InsideLabUpload2Action action = new InsideLabUpload2Action();
        action.withUploadedFiles(List.of(
                uploadedFile(validUpload.toFile(), "valid.hl7", "text/plain"),
                uploadedFile(hiddenUpload.toFile(), ".hidden.hl7", "text/plain")
        ));

        assertThat(action.getImportFiles()).hasSize(2);
        assertThat(action.getImportFiles().get(0).getCanonicalPath()).isEqualTo(validUpload.toFile().getCanonicalPath());
        assertThat(action.getImportFilesFileName()).containsExactly("valid.hl7", null);
        assertThat(action.getImportFilesContentType()).containsExactly("text/plain", "text/plain");
    }

    @Test
    @DisplayName("provider signature callback should capture image metadata")
    void shouldCaptureProviderSignatureUpload_whenCallbackReceivesValidFile() throws Exception {
        Path upload = Files.createTempFile(tempDir, "signature", ".png");
        UploadedFile uploadedFile = uploadedFile(upload.toFile(), "signature.png", "image/png");

        ProviderSignatureStamp2Action action = new ProviderSignatureStamp2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getImage()).isNotNull();
        assertThat(action.getImage().getCanonicalPath()).isEqualTo(upload.toFile().getCanonicalPath());
        assertThat(action.getImageFileName()).isEqualTo("signature.png");
        assertThat(action.getImageFileContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("flowsheet callback should redirect invalid filenames through flash error")
    void shouldSetFlashError_whenFlowsheetFilenameIsInvalid() throws Exception {
        Path upload = Files.createTempFile(tempDir, "flowsheet", ".xml");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull())).thenReturn(true);

        ManageFlowsheetsUpload2Action action = new ManageFlowsheetsUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile(upload.toFile(), ".hidden.xml", "application/xml")));

        String result = action.execute();

        assertThat(result).isEqualTo("none");
        assertThat(request.getSession().getAttribute("flashError"))
                .isEqualTo(PathValidationUtils.INVALID_FILENAME_MESSAGE);
        assertThat(response.getRedirectedUrl()).isEqualTo("ManageFlowsheets");
    }

    private static UploadedFile uploadedFile(Object content, String originalName, String contentType) {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(content);
        when(uploadedFile.getOriginalName()).thenReturn(originalName);
        when(uploadedFile.getContentType()).thenReturn(contentType);
        return uploadedFile;
    }
}
