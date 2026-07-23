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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Error-path unit tests for {@link EctConsultationFormFax2Action}: the guard that stops the action
 * NPE-ing on {@code Paths.get(null)} when the rendered fax PDF cannot be promoted into the document
 * store, surfacing a caller-diagnosable {@code "error"} result instead.
 */
@DisplayName("EctConsultationFormFax2Action")
@Tag("unit")
@Tag("fast")
class EctConsultationFormFax2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private SecurityInfoManager securityInfoManager;
    private ClinicDAO clinicDAO;
    private DocumentAttachmentManager documentAttachmentManager;
    private NioFileManager nioFileManager;
    private FaxJobDao faxJobDao;

    private EctConsultationFormFax2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/encounter/ConsultationFax");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        clinicDAO = mock(ClinicDAO.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        nioFileManager = mock(NioFileManager.class);
        faxJobDao = mock(FaxJobDao.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ClinicDAO.class, clinicDAO);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(NioFileManager.class, nioFileManager);
        registerMock(FaxJobDao.class, faxJobDao);
        registerMock(FaxConfigDao.class, mock(FaxConfigDao.class));
        registerMock(FaxManager.class, mock(FaxManager.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(clinicDAO.getClinic()).thenReturn(mock(Clinic.class));

        // Construct AFTER the static + Spring mocks are live: the action resolves its request,
        // response, and SpringUtils beans in field initializers.
        action = new EctConsultationFormFax2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return the error result when the rendered fax PDF cannot be promoted into the document store")
    void shouldReturnError_whenFaxPdfPromotionReturnsNull() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull())).thenReturn(true);
        Path rendered = Paths.get("/tmp/consult-fax-source.pdf");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(rendered);
        // copyFileToOscarDocuments returning null used to NPE the next line's Paths.get(null); the
        // guard must instead surface a caller-diagnosable "error" result.
        when(nioFileManager.copyFileToOscarDocuments(rendered.toString())).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("errorMessage")).asString()
                .contains("could not be stored");
        verify(nioFileManager).copyFileToOscarDocuments(rendered.toString());
        verify(faxJobDao, never()).persist(any());
    }
}
