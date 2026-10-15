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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PathL7EmbeddedDocumentMessage;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
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
 * Unit tests for {@link DownloadEmbeddedDocumentFromLab2Action}, the "Download PDF" route, after
 * it moved onto the shared embedded-document contract: attachment disposition, no size limit,
 * patient-scoped access, and the {@code %PDF-} check the former action lacked.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
@DisplayName("DownloadEmbeddedDocumentFromLab2Action")
class DownloadEmbeddedDocumentFromLab2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private Hl7TextMessageDao hl7TextMessageDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        hl7TextMessageDao = mock(Hl7TextMessageDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/lab/DownloadEmbeddedDocumentFromLab");
        request.setParameter("labNo", "789");
        request.setParameter("segment", "1");
        request.setParameter("group", "0");
        response = new MockHttpServletResponse();
        servletActionContextMock = org.mockito.Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = org.mockito.Mockito.mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private DownloadEmbeddedDocumentFromLab2Action action() {
        return new DownloadEmbeddedDocumentFromLab2Action(securityInfoManager, hl7TextMessageDao, patientLabRoutingDao);
    }

    private void storeLab(String hl7) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(hl7.getBytes(StandardCharsets.UTF_8)));
        when(hl7TextMessageDao.find(789)).thenReturn(message);
    }

    @Test
    @DisplayName("should download the PDF as an attachment and audit the read")
    void shouldServeAttachment_whenAuthorized() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any())).thenReturn(true);
        when(patientLabRoutingDao.findByLabNoAndLabType(789, "HL7")).thenReturn(List.of(new PatientLabRouting(789, "HL7", 55)));
        storeLab(PathL7EmbeddedDocumentMessage.message());

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"Lab-789.pdf\"");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getContentAsByteArray()).isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        logActionMock.verify(() -> LogAction.addLogStrict(loggedInInfo, LogConst.READ,
                AbstractEmbeddedLabDocumentAction.AUDIT_CONTENT, "789", "55", "segment=1,group=0,disposition=attachment"));
    }

    @Test
    @DisplayName("should apply no size limit to downloads")
    void shouldApplyNoSizeLimit_forDownload() {
        assertThat(action().maxBytes()).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("should download a PDF larger than the default inline preview limit in full")
    void shouldServeWholePdf_whenLargerThanPreviewLimit() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any())).thenReturn(true);
        // The view route refuses this size with 413 under the default settings; the download must not.
        byte[] large = Arrays.copyOf(PathL7EmbeddedDocumentMessage.PDF,
                Math.toIntExact(LabPdfPreviewSettings.DEFAULT_MAX_BYTES + 1));
        storeLab(PathL7EmbeddedDocumentMessage.message().replace(
                Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF),
                Base64.getEncoder().encodeToString(large)));

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentLength()).isEqualTo(large.length);
        assertThat(response.getContentAsByteArray()).isEqualTo(large);
    }

    @Test
    @DisplayName("should refuse to serve a non-PDF payload as a PDF download")
    void shouldReturn415_whenPayloadIsNotPdf() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any())).thenReturn(true);
        storeLab(PathL7EmbeddedDocumentMessage.message().replace(
                Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF),
                Base64.getEncoder().encodeToString("MZ not a pdf".getBytes(StandardCharsets.US_ASCII))));

        action().execute();

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer 400, not a NumberFormatException, for a non-numeric segment")
    void shouldReturn400_whenSegmentIsNotNumeric() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any())).thenReturn(true);
        request.setParameter("segment", "one");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should refuse a user without access to the matched patient")
    void shouldThrowSecurityException_whenPatientAccessMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)).thenReturn(true);
        when(patientLabRoutingDao.findByLabNoAndLabType(789, "HL7")).thenReturn(List.of(new PatientLabRouting(789, "HL7", 55)));

        assertThatThrownBy(() -> action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(hl7TextMessageDao);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
