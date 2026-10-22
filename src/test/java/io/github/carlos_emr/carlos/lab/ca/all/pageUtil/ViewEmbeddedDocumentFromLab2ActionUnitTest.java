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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PathL7EmbeddedDocumentMessage;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettingsService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link ViewEmbeddedDocumentFromLab2Action}, the inline PDF preview route, and
 * through it the contract of {@link AbstractEmbeddedLabDocumentAction}: method, privilege and
 * patient-scoped checks run before any lab is read, bad input is a 4xx rather than a 500, only a
 * verified PDF is written, and every served document is audited.
 *
 * <p>The lab is parsed by the real {@code Factory} and {@code PATHL7Handler} from
 * {@link PathL7EmbeddedDocumentMessage}: OBR 1 holds three ordinary results, OBR 2 the PDF.</p>
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
@DisplayName("ViewEmbeddedDocumentFromLab2Action")
class ViewEmbeddedDocumentFromLab2ActionUnitTest extends CarlosUnitTestBase {

    private static final int LAB_NO = 456;
    private static final int DEMOGRAPHIC_ID = 123;
    private static final String DEMOGRAPHIC_NO = String.valueOf(DEMOGRAPHIC_ID);

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private Hl7TextMessageDao hl7TextMessageDao;
    private PatientLabRoutingDao patientLabRoutingDao;
    private LabPdfPreviewSettingsService settingsService;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        hl7TextMessageDao = mock(Hl7TextMessageDao.class);
        patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        settingsService = mock(LabPdfPreviewSettingsService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(settingsService.load()).thenReturn(LabPdfPreviewSettings.DEFAULTS);

        request = new MockHttpServletRequest("GET", "/lab/ViewEmbeddedDocumentFromLab");
        request.setParameter("labNo", String.valueOf(LAB_NO));
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

    private ViewEmbeddedDocumentFromLab2Action action() {
        return new ViewEmbeddedDocumentFromLab2Action(securityInfoManager, hl7TextMessageDao, patientLabRoutingDao,
                settingsService);
    }

    private void grantAll() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);
    }

    private void storeLab(String hl7) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(hl7.getBytes(StandardCharsets.UTF_8)));
        when(hl7TextMessageDao.find(LAB_NO)).thenReturn(message);
    }

    private void matchToPatient() {
        when(patientLabRoutingDao.findByLabNoAndLabType(LAB_NO, "HL7"))
                .thenReturn(List.of(new PatientLabRouting(LAB_NO, "HL7", Integer.valueOf(DEMOGRAPHIC_ID))));
    }

    @Test
    @DisplayName("should serve a verified PDF inline with hardened headers and a read audit")
    void shouldServePdfInline_whenAuthorized() throws Exception {
        grantAll();
        matchToPatient();
        storeLab(PathL7EmbeddedDocumentMessage.message());

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
        assertThat(response.getContentLength()).isEqualTo(PathL7EmbeddedDocumentMessage.PDF.length);
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline; filename=\"Lab-456.pdf\"");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; frame-ancestors 'self'; sandbox");
        logActionMock.verify(() -> LogAction.addLogStrict(loggedInInfo, LogConst.READ,
                AbstractEmbeddedLabDocumentAction.AUDIT_CONTENT, "456", DEMOGRAPHIC_NO,
                "segment=1,group=0,disposition=inline"));
    }

    @Test
    @DisplayName("should write the read audit before any PDF header is set")
    void shouldAuditRead_beforeSettingPdfHeaders() throws Exception {
        grantAll();
        matchToPatient();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        List<String> typeAtAudit = new java.util.ArrayList<>();
        logActionMock.when(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                        anyString(), anyString()))
                .thenAnswer(invocation -> {
                    typeAtAudit.add(String.valueOf(response.getContentType()));
                    typeAtAudit.add(String.valueOf(response.getHeader("Content-Disposition")));
                    return null;
                });

        assertThat(action().execute()).isEqualTo("none");

        assertThat(typeAtAudit).containsExactly("null", "null");
        assertThat(response.getContentAsByteArray()).isEqualTo(PathL7EmbeddedDocumentMessage.PDF);
    }

    @Test
    @DisplayName("should answer a bare 500 with no PDF headers or bytes when the audit cannot be persisted")
    void shouldReturn500WithoutBody_whenReadAuditFails() throws Exception {
        grantAll();
        matchToPatient();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        logActionMock.when(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                        anyString(), anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic audit failure"));

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(500);
        // setStatus, not sendError: sendError would render the HTML error page into the frame.
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentLength()).isZero();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getHeader("Content-Security-Policy")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    @DisplayName("should check the matched patient's lab and demographic read privileges")
    void shouldCheckPatientScopedPrivileges_whenLabIsMatched() throws Exception {
        grantAll();
        matchToPatient();
        storeLab(PathL7EmbeddedDocumentMessage.message());

        action().execute();

        org.mockito.Mockito.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_lab", "r", null);
        org.mockito.Mockito.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_lab", "r", DEMOGRAPHIC_NO);
        org.mockito.Mockito.verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", DEMOGRAPHIC_NO);
    }

    @Test
    @DisplayName("should keep only the global lab check and audit without a patient for an unmatched lab")
    void shouldServeWithGlobalCheck_whenLabIsUnmatched() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", "r", null)).thenReturn(true);
        when(patientLabRoutingDao.findByLabNoAndLabType(LAB_NO, "HL7"))
                .thenReturn(List.of(new PatientLabRouting(LAB_NO, "HL7", 0)));
        storeLab(PathL7EmbeddedDocumentMessage.message());

        action().execute();

        assertThat(response.getStatus()).isEqualTo(200);
        logActionMock.verify(() -> LogAction.addLogStrict(any(LoggedInInfo.class), anyString(), anyString(), anyString(),
                isNull(), anyString()));
    }

    @Test
    @DisplayName("should refuse a user without lab read before reading anything")
    void shouldThrowSecurityException_whenLabPrivilegeMissing() {
        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");
        verifyNoInteractions(hl7TextMessageDao, patientLabRoutingDao);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"_lab, missing required sec object (_lab)",
            "_demographic, missing required sec object (_demographic)"})
    @DisplayName("should refuse a user without access to the matched patient before reading the lab")
    void shouldThrowSecurityException_whenPatientScopedPrivilegeMissing(String object, String message) {
        grantAll();
        when(securityInfoManager.hasPrivilege(loggedInInfo, object, "r", DEMOGRAPHIC_NO)).thenReturn(false);
        matchToPatient();

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage(message);
        verifyNoInteractions(hl7TextMessageDao);
        assertThat(response.getContentAsByteArray()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    @DisplayName("should reject methods other than GET and HEAD before any check")
    void shouldReturn405_forNonReadMethods(String method) throws Exception {
        request.setMethod(method);

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(securityInfoManager, hl7TextMessageDao, patientLabRoutingDao);
    }

    @ParameterizedTest
    @CsvSource({
            "labNo, abc", "labNo, ''", "labNo, 1234567890", "labNo, 4;5", "labNo, 0", "labNo, 000",
            "labNo, 99999999999999999999", "segment, 99999999999999999999",
            "segment, -1", "segment, 1.0", "group, x", "legacy, yes"
    })
    @DisplayName("should answer 400 for malformed parameters")
    void shouldReturn400_forMalformedParameters(String name, String value) throws Exception {
        grantAll();
        request.setParameter(name, value);

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(hl7TextMessageDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "000"})
    @DisplayName("should answer 400 for a non-positive labNo before querying routing or messages")
    void shouldReturn400_whenLabNoIsNotPositive(String labNo) throws Exception {
        grantAll();
        request.setParameter("labNo", labNo);

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(hl7TextMessageDao, patientLabRoutingDao);
    }

    @Test
    @DisplayName("should answer 400 for a missing parameter")
    void shouldReturn400_whenParameterMissing() throws Exception {
        grantAll();
        request.removeParameter("group");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should answer 400, not a ClassCastException, for legacy=true on a non-PATHL7 lab")
    void shouldReturn400_whenLegacyRequestedForNonPathL7Lab() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        request.setParameter("legacy", "true");
        MessageHandler other = mock(MessageHandler.class);
        ViewEmbeddedDocumentFromLab2Action action = new ViewEmbeddedDocumentFromLab2Action(securityInfoManager,
                hl7TextMessageDao, patientLabRoutingDao, settingsService) {
            @Override
            MessageHandler handler(Hl7TextMessage message) {
                return other;
            }
        };

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(other);
    }

    @Test
    @DisplayName("should honour legacy=true on a PATHL7 lab")
    void shouldServePdf_whenLegacyRequestedForPathL7Lab() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        request.setParameter("legacy", "true");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should answer 404 for an unknown lab")
    void shouldReturn404_whenLabUnknown() throws Exception {
        grantAll();

        action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "2, 0", "0, 9"})
    @DisplayName("should answer 404 for an OBX that is out of range or not ED")
    void shouldReturn404_whenObxIsNotAnEmbeddedDocument(String segment, String group) throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        request.setParameter("segment", segment);
        request.setParameter("group", group);

        action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsByteArray()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should answer 415 when the decoded ED payload is not a PDF")
    void shouldReturn415_whenPayloadIsNotPdf() throws Exception {
        grantAll();
        String html = Base64.getEncoder().encodeToString(
                "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.US_ASCII));
        storeLab(PathL7EmbeddedDocumentMessage.message().replace(
                Base64.getEncoder().encodeToString(PathL7EmbeddedDocumentMessage.PDF), html));

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should answer 413 when the PDF is over the preview limit")
    void shouldReturn413_whenPdfExceedsPreviewLimit() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        when(settingsService.load()).thenReturn(new LabPdfPreviewSettings(true, 16));

        action().execute();

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should answer 404 when the inline preview is turned off")
    void shouldReturn404_whenPreviewDisabled() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        when(settingsService.load()).thenReturn(new LabPdfPreviewSettings(false, 1024 * 1024));

        action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(hl7TextMessageDao);
    }

    @Test
    @DisplayName("should answer HEAD with headers only and no audit record")
    void shouldWriteHeadersOnly_forHead() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        request.setMethod("HEAD");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should size HEAD from the non-retaining inspection, matching GET, without loading the bytes")
    void shouldAnswerHeadWithoutLoading_whenSizingTheDocument() throws Exception {
        grantAll();
        storeLab(PathL7EmbeddedDocumentMessage.message());
        action().execute();
        assertThat(response.getStatus()).isEqualTo(200);
        int getLength = response.getContentAsByteArray().length;
        assertThat(getLength).isPositive();
        assertThat(response.getContentLength()).isEqualTo(getLength);

        response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        request.setMethod("HEAD");
        try (MockedStatic<EmbeddedLabDocumentLoader> loaderMock = org.mockito.Mockito.mockStatic(
                EmbeddedLabDocumentLoader.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            action().execute();
            loaderMock.verify(() -> EmbeddedLabDocumentLoader.load(any(), anyInt(), anyInt(),
                    org.mockito.ArgumentMatchers.anyLong()), org.mockito.Mockito.never());
        }

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentLength()).isEqualTo(getLength);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should not query anything when the lab id is malformed even with routing present")
    void shouldNotResolveRouting_whenLabNoMalformed() throws Exception {
        grantAll();
        request.setParameter("labNo", "1 OR 1=1");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(400);
        org.mockito.Mockito.verify(patientLabRoutingDao, org.mockito.Mockito.never())
                .findByLabNoAndLabType(anyInt(), anyString());
    }
}
