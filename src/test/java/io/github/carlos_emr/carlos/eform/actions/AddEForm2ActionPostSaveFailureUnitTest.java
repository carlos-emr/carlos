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
package io.github.carlos_emr.carlos.eform.actions;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormSubmissionGuard;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A failure after the eForm is stored must not be answered as a replayed submission (issue #4424,
 * {@code docs/ui-tests/app-findings-log.md} finding 168).
 *
 * <p>Once storage has started, {@code AddEForm2Action} keeps the submission token consumed so a
 * retry cannot save the eForm a second time. It used to answer every such failure with the replay
 * refusal ({@code eform.submitUnavailable}, "This form can no longer be submitted from this page"),
 * so a clinician whose Add to Documents, chart-note or download step failed was told, in effect,
 * that they had pressed Submit twice and nothing had gone wrong.</p>
 *
 * <p>{@code getText} is answered from the shipped English bundle and formatted through
 * {@link MessageFormat}, as Struts formats it, so the text asserted here is the text the clinician
 * reads, apostrophes included. Failure messages carry synthetic patient details and the patient and
 * eForm ids are distinctive numbers, so a log or response that leaked them would be caught.</p>
 *
 * @since 2026-10-19
 */
@DisplayName("AddEForm2Action failure after the eForm is stored")
@Tag("unit")
@Tag("eform")
class AddEForm2ActionPostSaveFailureUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "8675309";
    private static final int SAVED_FDID = 9081726;
    /** What DocumentManagerImpl.moveDocument's refusal carries (the provider number), plus synthetic patient details. */
    private static final String PRIVATE_DETAIL = "Read and Write Access Denied _edoc for provider doc1 FAKE-Jane HIN 9876543217";
    private static final String REPLAY_FIRST_SENTENCE = "This form can no longer be submitted from this page";
    private static final ResourceBundle ENGLISH = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<EFormLoader> eFormLoaderMock;
    private MockedStatic<WebApplicationContextUtils> webApplicationContextUtilsMock;
    private AutoCloseable mockitoMocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EformDataManager mockEformDataManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private EmailManager mockEmailManager;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private RecordingTransactionManager transactions;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoMocks = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        transactions = new RecordingTransactionManager();
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EformDataManager.class, mockEformDataManager);
        registerMock(PlatformTransactionManager.class, transactions);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(EmailManager.class, mockEmailManager);
        registerMock(DemographicManager.class, mockDemographicManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenAnswer(call -> mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");

        eFormUtilMock = mockStatic(EFormUtil.class);
        HashMap<String, Object> eformData = new HashMap<>();
        eformData.put("formName", "TestForm");
        eformData.put("formHtml", "");
        eformData.put("formSubject", "");
        eformData.put("formDate", "");
        eformData.put("formFileName", "test.html");
        eformData.put("formCreator", "doc1");
        eFormUtilMock.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(eformData);
        eFormUtilMock.when(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()))
                .then(invocation -> null);

        eFormLoaderMock = mockStatic(EFormLoader.class);
        eFormLoaderMock.when(EFormLoader::getInstance).thenReturn(mock(EFormLoader.class));
        eFormLoaderMock.when(EFormLoader::getOpener).thenReturn("oscarOPEN=");

        // The template write resolves EctProgram's manager from the servlet context (not SpringUtils);
        // an empty program list keeps it off the database. Same arrangement as AddEForm2ActionTemplateWriteUnitTest.
        EctProgramManager programManager = mock(EctProgramManager.class);
        when(programManager.getProgramBeans(anyString(), isNull())).thenReturn(List.of());
        WebApplicationContext applicationContext = mock(WebApplicationContext.class);
        when(applicationContext.getBean("ectProgramManager")).thenReturn(programManager);
        webApplicationContextUtilsMock = mockStatic(WebApplicationContextUtils.class);
        webApplicationContextUtilsMock
                .when(() -> WebApplicationContextUtils.getWebApplicationContext(any()))
                .thenReturn(applicationContext);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockEformDataManager.saveEformData(any(LoggedInInfo.class), any())).thenReturn(SAVED_FDID);

        mockRequest.setParameter("efmfid", "1");
        mockRequest.setParameter("efmdemographic_no", PATIENT);
        mockRequest.setParameter(EFormSubmissionGuard.PARAMETER,
                EFormSubmissionGuard.issue(mockRequest.getSession(), "1", PATIENT));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webApplicationContextUtilsMock != null) webApplicationContextUtilsMock.close();
        if (eFormLoaderMock != null) eFormLoaderMock.close();
        if (eFormUtilMock != null) eFormUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoMocks != null) mockitoMocks.close();
    }

    /** The English bundle text for {@code key}, formatted the way Struts getText formats it. */
    private static String bundleText(String key, Object... arguments) {
        return new MessageFormat(ENGLISH.getString(key), Locale.ENGLISH).format(arguments);
    }

    /** An action whose getText answers from the shipped English bundle (there is no Struts container here). */
    private AddEForm2Action action() {
        AddEForm2Action action = spy(new AddEForm2Action());
        doAnswer(call -> bundleText(call.getArgument(0))).when(action).getText(anyString());
        doAnswer(call -> bundleText(call.getArgument(0), (Object[]) call.getArgument(1)))
                .when(action).getText(anyString(), any(String[].class));
        return action;
    }

    private void failAddToDocuments() throws Exception {
        mockRequest.setParameter("saveAsEdoc", "true");
        doThrow(new RuntimeException(PRIVATE_DETAIL)).when(mockDocumentAttachmentManager).saveEFormAsEDoc(any(), any());
    }

    private void assertNonSuccessPlainText() {
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        assertThat(mockResponse.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(mockResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(mockResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    private void verifyTemplateWrites(int expected) {
        eFormUtilMock.verify(
                () -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), anyString(), anyString(), anyString()),
                times(expected));
    }

    @Test
    @DisplayName("should answer a replayed submission with the unchanged replay refusal")
    void shouldAnswerReplayWithUnchangedRefusal_whenSavedTokenIsPostedAgain() throws Exception {
        mockRequest.setParameter("faxEForm", "true");
        assertThat(action().execute()).isEqualTo("faxPreparation");

        mockResponse = new MockHttpServletResponse();
        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString()).isEqualTo(bundleText("eform.submitUnavailable"))
                .startsWith(REPLAY_FIRST_SENTENCE).contains("Check the patient's eForms");
        verify(mockEformDataManager, times(1)).saveEformData(any(), any());
    }

    @Test
    @DisplayName("should say the eForm was saved but Add to Documents did not complete")
    void shouldNameAddToDocuments_whenEdocStepFailsAfterEFormIsStored() throws Exception {
        failAddToDocuments();

        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString())
                .startsWith("The eForm was saved, but Add to Documents did not complete.")
                .contains("patient's")
                .doesNotContain(REPLAY_FIRST_SENTENCE, PRIVATE_DETAIL, "FAKE-Jane", PATIENT);
        verify(mockEformDataManager, times(1)).saveEformData(any(), any());
        verifyTemplateWrites(1);
    }

    @Test
    @DisplayName("should keep the token consumed after a follow-on failure, so a retry is the replay refusal and saves nothing")
    void shouldKeepTokenConsumed_whenFollowOnStepFails() throws Exception {
        failAddToDocuments();
        assertThat(action().execute()).isEqualTo("none");

        mockResponse = new MockHttpServletResponse();
        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString()).isEqualTo(bundleText("eform.submitUnavailable"));
        verify(mockEformDataManager, times(1)).saveEformData(any(), any());
        verify(mockDocumentAttachmentManager, times(1)).saveEFormAsEDoc(any(), any());
        verifyTemplateWrites(1);
    }

    @Test
    @DisplayName("should log the failed step and the stack trace without the exception message or patient ids")
    void shouldLogStackTraceWithoutPhi_whenFollowOnStepFails() throws Exception {
        failAddToDocuments();

        try (LogCapture logs = LogCapture.forLogger(AddEForm2Action.class)) {
            assertThat(action().execute()).isEqualTo("none");

            List<LogEvent> errors = logs.events().stream()
                    .filter(event -> event.getLevel() == Level.ERROR).toList();
            assertThat(errors).hasSize(1);
            String logged = errors.getFirst().getMessage().getFormattedMessage();
            assertThat(logged)
                    .contains("ADD_TO_DOCUMENTS", "java.lang.RuntimeException", "\n  at ")
                    .contains("AddEForm2Action");
            // The throwable itself is never attached: rendering it would print its message and causes.
            assertThat(errors.getFirst().getThrown()).isNull();
            assertThat(logs.messages().toString())
                    .doesNotContain(PRIVATE_DETAIL, "FAKE-Jane", "9876543217", "doc1", PATIENT,
                            String.valueOf(SAVED_FDID));
        }
    }

    @Test
    @DisplayName("should say the eForm was saved but its chart entries were not written when the template write fails")
    void shouldNameChartEntries_whenTemplateWriteFailsAfterEFormIsStored() throws Exception {
        eFormUtilMock.when(() -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException(PRIVATE_DETAIL));

        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString())
                .startsWith("The eForm was saved, but writing its chart notes, ticklers and other entries did not complete.")
                .doesNotContain(REPLAY_FIRST_SENTENCE, PRIVATE_DETAIL);
    }

    @Test
    @DisplayName("should say the eForm was saved but the PDF download did not complete when the download render throws")
    void shouldNameDownload_whenDownloadRenderFailsAfterEFormIsStored() throws Exception {
        mockRequest.setParameter("saveAndDownloadEForm", "true");
        when(mockDocumentAttachmentManager.renderEFormPacketWithCompleteness(any(), any(), isNull()))
                .thenThrow(new IllegalStateException(PRIVATE_DETAIL));

        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString())
                .startsWith("The eForm was saved, but preparing the PDF download did not complete.")
                .doesNotContain(REPLAY_FIRST_SENTENCE, PRIVATE_DETAIL);
        verifyTemplateWrites(0);
    }

    @Test
    @DisplayName("should say the eForm was saved but a follow-on step did not complete when a commit callback throws")
    void shouldNameGenericFollowOnStep_whenCommitCallbackThrows() throws Exception {
        mockRequest.setParameter("faxEForm", "true");
        when(mockEformDataManager.saveEformData(any(), any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    throw new IllegalArgumentException(PRIVATE_DETAIL);
                }
            });
            return SAVED_FDID;
        });

        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(mockResponse.getContentAsString())
                .startsWith("The eForm was saved, but a follow-on step did not complete.")
                .doesNotContain(REPLAY_FIRST_SENTENCE, PRIVATE_DETAIL);
    }

    @Test
    @DisplayName("should say the save could not be confirmed, not that it was saved, when the commit outcome is unknown")
    void shouldReportUnconfirmedSave_whenCommitAcknowledgementIsLost() throws Exception {
        mockRequest.setParameter("faxEForm", "true");
        transactions.failCommit = true;

        assertThat(action().execute()).isEqualTo("none");

        assertNonSuccessPlainText();
        assertThat(mockResponse.getContentAsString())
                .startsWith("CARLOS could not confirm whether this eForm was saved.")
                .contains("Check the patient's eForms")
                .doesNotContain(REPLAY_FIRST_SENTENCE, "was saved, but", "not saved");

        mockResponse = new MockHttpServletResponse();
        assertThat(action().execute()).isEqualTo("none");
        assertThat(mockResponse.getContentAsString()).isEqualTo(bundleText("eform.submitUnavailable"));
        verify(mockEformDataManager, times(1)).saveEformData(any(), any());
    }
}
