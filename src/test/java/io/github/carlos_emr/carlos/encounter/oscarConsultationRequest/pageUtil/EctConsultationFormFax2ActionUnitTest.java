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
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FilePromotionException;
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
    private io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao consultationRequestDao;
    private DocumentAttachmentManager documentAttachmentManager;
    private NioFileManager nioFileManager;
    private FaxJobDao faxJobDao;
    private FaxConfigDao faxConfigDao;
    private FaxManager faxManager;

    @org.junit.jupiter.api.io.TempDir
    Path temporaryDirectory;

    private EctConsultationFormFax2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/encounter/ConsultationFax");
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        clinicDAO = mock(ClinicDAO.class);
        consultationRequestDao = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao.class);
        io.github.carlos_emr.carlos.commn.model.ConsultationRequest consultation =
                new io.github.carlos_emr.carlos.commn.model.ConsultationRequest();
        consultation.setDemographicId(123);
        when(consultationRequestDao.find(456)).thenReturn(consultation);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        nioFileManager = mock(NioFileManager.class);
        faxJobDao = mock(FaxJobDao.class);
        faxConfigDao = mock(FaxConfigDao.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ClinicDAO.class, clinicDAO);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(NioFileManager.class, nioFileManager);
        registerMock(FaxJobDao.class, faxJobDao);
        registerMock(FaxConfigDao.class, faxConfigDao);
        faxManager = mock(FaxManager.class);
        registerMock(FaxManager.class, faxManager);

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
        action.setDemographicNo("123");
        action.setRequestId("456");
        action.setSenderFaxNumber("1234567890");
        action.setRecipient("Test Recipient");
        action.setRecipientFaxNumber("9876543210");

        FaxConfig config = mock(FaxConfig.class);
        when(config.isActive()).thenReturn(true);
        when(config.getFaxNumber()).thenReturn("1234567890");
        when(config.getAccountName()).thenReturn("Test Account");
        when(faxConfigDao.findAll(null, null)).thenReturn(java.util.List.of(config));
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, "123")).thenReturn(true);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"!!!!!!!", "+1 (23)-4", "       ", "inactive", "null-sender"})
    @DisplayName("should reject invalid normalized destinations and inactive sender accounts before file promotion")
    void shouldRejectInvalidFaxDetails_beforePromotion(String scenario) throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        if ("inactive".equals(scenario) || "null-sender".equals(scenario)) {
            FaxConfig config = mock(FaxConfig.class);
            when(config.getFaxNumber()).thenReturn("inactive".equals(scenario) ? "1234567890" : null);
            when(config.isActive()).thenReturn(!"inactive".equals(scenario));
            when(faxConfigDao.findAll(null, null)).thenReturn(java.util.List.of(config));
        } else {
            action.setRecipientFaxNumber(scenario);
        }
        assertThat(action.execute()).isEqualTo("error");
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, nioFileManager, faxJobDao);
        verify(faxManager, never()).persistAndLogConsultationFaxJobs(any(), any(), org.mockito.ArgumentMatchers.anyInt());
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1234567", "12345678", "123456789", "1234567890123456"})
    @DisplayName("should reject destinations outside SRFax rules before publishing documents")
    void shouldRejectSrfaxDestination_beforePromotion(String destination) throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        FaxConfig selected = faxConfigDao.findAll(null, null).get(0);
        when(selected.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);
        action.setRecipientFaxNumber(destination);
        assertThat(action.execute()).isEqualTo("error");
        org.mockito.Mockito.verifyNoInteractions(nioFileManager, faxJobDao);
        verify(faxManager, never()).persistAndLogConsultationFaxJobs(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("should require patient-scoped consultation write before standalone faxing but preserve cancel")
    void shouldRejectStandaloneFax_whenConsultationWriteIsDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.WRITE, "123")).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required consultation write access");
        org.mockito.Mockito.verifyNoInteractions(consultationRequestDao, documentAttachmentManager, nioFileManager, faxConfigDao, faxJobDao);
        action.setMethod("cancel");
        assertThat(action.execute()).isEqualTo("cancel");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing", "wrong-patient", "null-patient", "invalid-id", "conflicting-alias"})
    @DisplayName("should reject an unbound consultation before rendering or reading fax accounts")
    void shouldRejectConsultation_whenNotBoundToAuthorizedPatient(String scenario) {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        io.github.carlos_emr.carlos.commn.model.ConsultationRequest consultation =
                new io.github.carlos_emr.carlos.commn.model.ConsultationRequest();
        consultation.setDemographicId("wrong-patient".equals(scenario) ? Integer.valueOf(999)
                : "conflicting-alias".equals(scenario) ? Integer.valueOf(123) : null);
        when(consultationRequestDao.find(456)).thenReturn("missing".equals(scenario) ? null : consultation);
        if ("invalid-id".equals(scenario)) {
            action.setRequestId("not-an-id");
        }
        if ("conflicting-alias".equals(scenario)) {
            request.setParameter("reqId", "999");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, nioFileManager, faxConfigDao, faxJobDao, clinicDAO);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS", "post"})
    @DisplayName("should reject every non-POST fax submission before rendering or queueing")
    void shouldRejectFax_beforeSideEffectsWhenMethodIsNotPost(String method) {
        request.setMethod(method);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, nioFileManager, faxConfigDao, faxJobDao);
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(), org.mockito.ArgumentMatchers.anyInt());
        action.setMethod("cancel");
        assertThat(action.execute()).isEqualTo("cancel");
    }

    @Test
    @DisplayName("should reject final fax submission before rendering when account read is denied")
    void shouldRejectFax_beforeRenderingWhenFaxReadIsDenied() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_fax)");
        org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, nioFileManager, faxConfigDao, faxJobDao);
        action.setMethod("cancel");
        assertThat(action.execute()).isEqualTo("cancel");
    }

    @Test
    @DisplayName("should reject faxing before rendering when consultation fax is disabled")
    void shouldRejectFax_beforeRenderingWhenFeatureIsDisabled() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        io.github.carlos_emr.CarlosProperties properties = mock(io.github.carlos_emr.CarlosProperties.class);
        when(properties.isConsultationFaxEnabled()).thenReturn(false);
        try (MockedStatic<io.github.carlos_emr.CarlosProperties> propertiesMock = mockStatic(io.github.carlos_emr.CarlosProperties.class)) {
            propertiesMock.when(io.github.carlos_emr.CarlosProperties::getInstance).thenReturn(properties);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> action.execute())
                    .isInstanceOf(SecurityException.class).hasMessage("consultation fax is disabled");
            verify(properties).isConsultationFaxEnabled();
            org.mockito.Mockito.verifyNoInteractions(documentAttachmentManager, nioFileManager, faxJobDao);
            action.setMethod("cancel");
            assertThat(action.execute()).isEqualTo("cancel");
        }
    }

    @Test
    @DisplayName("should not expose renderer exception details to the browser or logs")
    void shouldHideRendererDetails_whenPdfGenerationFails() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response))
                .thenThrow(new io.github.carlos_emr.carlos.utility.PDFGenerationException(
                        "SensitiveFixturePatient /private/attachment.pdf token=fixture-secret"));
        try (io.github.carlos_emr.carlos.test.logging.LogCapture capture =
                io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(EctConsultationFormFax2Action.class)) {
            assertThat(action.execute()).isEqualTo("error");
            assertThat(capture.messages()).anyMatch(message -> message.contains("PDF preparation failed"));
            assertThat(capture.messages().toString())
                    .doesNotContain("SensitiveFixturePatient", "/private/", "fixture-secret", "attachment.pdf");
            assertThat(capture.events()).allMatch(event -> event.getThrown() == null);
        }
        assertThat(request.getAttribute("errorMessage")).asString()
                .contains("consultation PDF could not be prepared")
                .doesNotContain("SensitiveFixturePatient", "/private/", "fixture-secret", "attachment.pdf");
        org.mockito.Mockito.verifyNoInteractions(nioFileManager, faxJobDao);
        verify(faxManager, never()).persistAndLogConsultationFaxJobs(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("should return the error result when the rendered fax PDF cannot be promoted into the document store")
    void shouldReturnError_whenFaxPdfPromotionReturnsNull() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull())).thenReturn(true);
        // Faxing now also requires _fax write (mirrors Fax2Action); grant it so the test reaches the
        // PDF-promotion path it is exercising rather than stopping at the authorization gate.
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        Path rendered = Paths.get("/tmp/consult-fax-source.pdf");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(rendered);
        when(nioFileManager.promoteApplicationTempFile(rendered))
                .thenThrow(new FilePromotionException("test failure"));

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(request.getAttribute("errorMessage")).asString()
                .contains("could not be stored");
        verify(nioFileManager).promoteApplicationTempFile(rendered);
        verify(faxJobDao, never()).persist(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"application-temp", "outside", "symlink"})
    @DisplayName("should remove only contained application-temp PDFs when promotion fails")
    void shouldCleanRenderedSource_whenPromotionFails(String scenario) throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        Path root = java.nio.file.Files.createDirectories(Path.of(System.getProperty("java.io.tmpdir"), "carlos-temp"));
        Path ownedDirectory = java.nio.file.Files.createTempDirectory(root, "consult-fax-test-");
        Path outside = java.nio.file.Files.writeString(temporaryDirectory.resolve("outside.pdf"), "outside fixture");
        Path rendered = "outside".equals(scenario) ? outside : ownedDirectory.resolve("rendered.pdf");
        if ("symlink".equals(scenario)) java.nio.file.Files.createSymbolicLink(rendered, outside);
        else if (!"outside".equals(scenario)) java.nio.file.Files.writeString(rendered, "rendered fixture");
        try {
            when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(rendered);
            when(nioFileManager.promoteApplicationTempFile(rendered)).thenThrow(new FilePromotionException("fixture failure"));
            assertThat(action.execute()).isEqualTo("error");
            assertThat(java.nio.file.Files.exists(rendered)).isEqualTo(!"application-temp".equals(scenario));
            assertThat(java.nio.file.Files.readString(outside)).isEqualTo("outside fixture");
            verify(faxManager, never()).persistAndLogConsultationFaxJobs(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        } finally {
            if (!"outside".equals(scenario)) java.nio.file.Files.deleteIfExists(rendered);
            java.nio.file.Files.deleteIfExists(ownedDirectory);
        }
    }

    @Test
    void shouldRetainFaxArtifacts_whenCommitAcknowledgementIsUncertain() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        Path pdf = java.nio.file.Files.writeString(temporaryDirectory.resolve("consult.pdf"), "fixture PDF");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(pdf);
        when(nioFileManager.promoteApplicationTempFile(pdf)).thenReturn(pdf);
        org.mockito.Mockito.doThrow(new IllegalStateException("commit acknowledgement lost"))
                .when(faxManager).persistAndLogConsultationFaxJobs(eq(loggedInInfo), any(), eq(456));
        try (MockedStatic<io.github.carlos_emr.carlos.documentManager.EDocUtil> edoc = mockStatic(io.github.carlos_emr.carlos.documentManager.EDocUtil.class);
             MockedStatic<io.github.carlos_emr.carlos.utility.PathValidationUtils> paths = mockStatic(io.github.carlos_emr.carlos.utility.PathValidationUtils.class)) {
            edoc.when(() -> io.github.carlos_emr.carlos.documentManager.EDocUtil.getPDFPageCount(pdf.toString())).thenReturn(1);
            paths.when(() -> io.github.carlos_emr.carlos.utility.PathValidationUtils.validateExistingPath(any(java.io.File.class), any(java.io.File.class)))
                    .thenReturn(pdf.toFile());
            paths.when(() -> io.github.carlos_emr.carlos.utility.PathValidationUtils.validateApplicationTempPath(pdf.toFile()))
                    .thenThrow(new SecurityException("fixture is outside application temp"));
            String result = action.execute();
            assertThat(pdf).exists();
            assertThat(result).isEqualTo("faxUncertain");
            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(request.getAttribute("faxSuccessful")).isNull();
            paths.verify(() -> io.github.carlos_emr.carlos.utility.PathValidationUtils.validateExistingPath(
                    any(java.io.File.class), any(java.io.File.class)), never());
            verify(faxManager).persistAndLogConsultationFaxJobs(eq(loggedInInfo), any(), eq(456));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MIDDLEWARE", "SRFAX"})
    void shouldReportQueued_whenSecondaryAuditFailsAfterCommit(String providerType) throws Exception {
        boolean srfax = "SRFAX".equals(providerType);
        action.setRecipientFaxNumber(srfax ? "+44 20 7946 0100" : "+1 (987) 654-3210");
        if (srfax) action.setFaxRecipients(new String[]{"\"name\":\"Duplicate\",\"fax\":\"011442079460100\""});
        FaxConfig selected = faxConfigDao.findAll(null, null).get(0);
        when(selected.getProviderType()).thenReturn(FaxConfig.ProviderType.valueOf(providerType));
        when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_fax"), eq("r"), isNull())).thenReturn(true);
        Path pdf = java.nio.file.Files.writeString(temporaryDirectory.resolve("queued.pdf"), "fixture PDF");
        when(documentAttachmentManager.renderConsultationFormWithAttachments(request, response)).thenReturn(pdf);
        when(nioFileManager.promoteApplicationTempFile(pdf)).thenReturn(pdf);
        try (MockedStatic<io.github.carlos_emr.carlos.documentManager.EDocUtil> edoc = mockStatic(io.github.carlos_emr.carlos.documentManager.EDocUtil.class)) {
            edoc.when(() -> io.github.carlos_emr.carlos.documentManager.EDocUtil.getPDFPageCount(pdf.toString())).thenReturn(1);
            logActionMock.when(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("999998",
                    io.github.carlos_emr.carlos.log.LogConst.SENT,
                    io.github.carlos_emr.carlos.log.LogConst.CON_FAX, "CONSULT 456"))
                    .thenThrow(new IllegalStateException("secondary log unavailable"));
            assertThat(action.execute()).isEqualTo("success");
            assertThat(pdf).exists();
            assertThat(request.getAttribute("faxSuccessful")).isEqualTo(true);
            verify(faxManager).persistAndLogConsultationFaxJobs(eq(loggedInInfo),
                    org.mockito.ArgumentMatchers.argThat(jobs -> jobs.size() == 1
                            && (srfax ? "+442079460100" : "19876543210").equals(jobs.get(0).getDestination())), eq(456));
        }
    }

    @Test
    void shouldUseNonRetryingWarningView_whenFaxOutcomeIsUncertain() throws Exception {
        String mapping = java.nio.file.Files.readString(Paths.get("src/main/webapp/WEB-INF/classes/struts-encounter.xml"));
        assertThat(mapping).contains("<result name=\"faxUncertain\">/WEB-INF/jsp/encounter/oscarConsultationRequest/FaxSubmissionUncertain.jsp</result>");
        String view = java.nio.file.Files.readString(Paths.get("src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/FaxSubmissionUncertain.jsp"));
        assertThat(view).contains("id=\"consult-fax-uncertain\"", "consultation.fax.uncertain.message", "ViewDisplayDemographicConsultationRequests")
                .contains("<html lang=\"<carlos:encode", "pageContext.request.locale.toLanguageTag()");
        assertThat(view).doesNotContain("<form", "setTimeout", "history.back", "finishPage(");
    }
}
