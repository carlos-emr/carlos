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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The attachment download names a report by its hash. It refuses a report filed to a patient the
 * provider is locked out of before the report row or its file is read.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRMDownloadFile2Action patient access")
class HRMDownloadFile2ActionPatientAccessUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4242;
    private static final String HASH = "fake0000000000000000000000000000000000000000000000000000000000ff";
    private static final byte[] ATTACHMENT = "%PDF-1.4 FAKE attachment".getBytes(StandardCharsets.US_ASCII);

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final HRMDocumentDao documents = mock(HRMDocumentDao.class);
    private final HRMDocumentToDemographicDao links = mock(HRMDocumentToDemographicDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<HRMReportParser> parser;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(HRMDocumentDao.class, documents);
        registerMock(HRMDocumentToDemographicDao.class, links);
        request = new MockHttpServletRequest("GET", "/hospitalReportManager/HRMDownloadFile");
        request.setParameter("hash", HASH);
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(true);

        HRMDocument document = mock(HRMDocument.class);
        when(document.getId()).thenReturn(7);
        when(document.getReportFile()).thenReturn("FAKE-report.xml");
        when(documents.findByHash(HASH)).thenReturn(List.of(7));
        // The action looks the report up with an Integer, which selects find(Object), not find(int).
        when(documents.find((Object) Integer.valueOf(7))).thenReturn(document);
        HRMReport report = mock(HRMReport.class);
        when(report.isBinary()).thenReturn(true);
        when(report.getBinaryContent()).thenReturn(ATTACHMENT);
        when(report.getFileExtension()).thenReturn(".pdf");
        when(report.getLegalLastName()).thenReturn("FAKE-Last");
        when(report.getLegalFirstName()).thenReturn("FAKE-First");
        when(report.getFirstReportClass()).thenReturn("FAKE-Class");
        parser = mockStatic(HRMReportParser.class);
        parser.when(() -> HRMReportParser.parseReport(user, "FAKE-report.xml")).thenReturn(report);
    }

    @AfterEach
    void tearDown() {
        parser.close();
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private void fileReportTo(Integer patient) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(7);
        link.setDemographicNo(patient);
        when(links.findByHrmDocumentId(7)).thenReturn(List.of(link));
    }

    private void permitPatient() {
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(true);
    }

    @Test
    @DisplayName("should serve the attachment of a report filed to a patient the provider may read")
    void shouldServeAttachment_whenPatientIsReadable() throws Exception {
        fileReportTo(PATIENT);
        permitPatient();

        assertThat(new HRMDownloadFile2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsByteArray()).isEqualTo(ATTACHMENT);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should refuse the attachment of a report filed to a patient the provider is locked out of")
    void shouldRefuse_whenPatientIsLocked() {
        fileReportTo(PATIENT);
        permitPatient();
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> new HRMDownloadFile2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required patient read access");

        // Nothing of the report was read or written: no row, no file, no header naming the patient.
        verify(documents, never()).find(any(Object.class));
        verify(documents, never()).find(anyInt());
        parser.verify(() -> HRMReportParser.parseReport(any(LoggedInInfo.class), anyString()), never());
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    @DisplayName("should serve the attachment of a report filed to no patient on the module right alone")
    void shouldServeAttachment_whenReportIsFiledToNoPatient() throws Exception {
        when(links.findByHrmDocumentId(7)).thenReturn(List.of());

        assertThat(new HRMDownloadFile2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsByteArray()).isEqualTo(ATTACHMENT);
        verify(security, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }
}
