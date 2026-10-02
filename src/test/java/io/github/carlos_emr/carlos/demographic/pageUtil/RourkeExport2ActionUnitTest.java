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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dao.forms.Rourke2009DAO;
import io.github.carlos_emr.carlos.commn.model.DataExport;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
class RourkeExport2ActionUnitTest extends CarlosUnitTestBase {
    private static final String FILE = "rourke2009_export-2026-10-02.12.30.00.zip";
    @TempDir Path directory;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private DataExportDao exports;
    private ClinicDAO clinic;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<CarlosProperties> properties;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        exports = createAndRegisterMock(DataExportDao.class);
        clinic = createAndRegisterMock(ClinicDAO.class);
        createAndRegisterMock(DemographicDao.class);
        createAndRegisterMock(Rourke2009DAO.class);
        request = new MockHttpServletRequest("GET", "/demographic/eRourkeExport");
        request.setParameter("method", "getFile");
        request.setParameter("zipFile", FILE);
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        CarlosProperties config = mock(CarlosProperties.class);
        when(config.getProperty("DOCUMENT_DIR")).thenReturn(directory.toString());
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);
        when(security.hasPrivilege(loggedInInfo, "_demographic", "r", null)).thenReturn(true);
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        DataExport export = new DataExport();
        export.setFile(FILE);
        when(exports.findAllByType(DataExportDao.ROURKE)).thenReturn(List.of(export));
    }

    @AfterEach
    void tearDown() {
        properties.close();
        login.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectExportMutation_beforeQueryingPersistence(String method) throws Exception {
        request.setMethod(method);
        request.removeParameter("method");
        RourkeExport2Action action = new RourkeExport2Action();
        action.setPatientSet("owned-set");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(exports, clinic);
    }

    @Test
    void shouldDownloadRecordedExport_whenDispatchedThroughExecute() throws Exception {
        byte[] bytes = {80, 75, 3, 4, 0, 1};
        Files.write(directory.resolve(FILE), bytes);
        assertThat(new RourkeExport2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
        assertThat(response.getContentType()).isEqualTo("application/zip");
        assertThat(response.getHeader("Content-Disposition")).contains(FILE);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(exports, never()).persist(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../secret.zip", "other.zip", "rourke2009_export-1.zip/../secret", "rourke2009_export-1.zip\r\nHeader: injected"})
    void shouldRejectInvalidFilename_whenDownloadRequested(String filename) throws Exception {
        request.setParameter("zipFile", filename);
        new RourkeExport2Action().execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(exports);
    }

    @Test
    void shouldRejectUnrecordedExport_whenFileExists() throws Exception {
        Files.writeString(directory.resolve(FILE), "synthetic export");
        when(exports.findAllByType(DataExportDao.ROURKE)).thenReturn(List.of());
        new RourkeExport2Action().execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenRecordedFileMissing() throws Exception {
        new RourkeExport2Action().execute();
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void shouldRejectEscapingSymlink_whenRecordedFilePointsOutsideDirectory(@TempDir Path outside) throws Exception {
        Path secret = Files.writeString(outside.resolve("secret"), "synthetic secret");
        Files.createSymbolicLink(directory.resolve(FILE), secret);
        new RourkeExport2Action().execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_admin"})
    void shouldDenyDownload_whenPrivilegeMissing(String privilege) {
        when(security.hasPrivilege(loggedInInfo, privilege, "r", null)).thenReturn(false);
        assertThatThrownBy(() -> new RourkeExport2Action().getFile()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(exports);
    }
}
