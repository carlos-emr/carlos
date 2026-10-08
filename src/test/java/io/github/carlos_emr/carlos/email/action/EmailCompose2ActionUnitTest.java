/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.action;

import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.PdfPreviewCapabilityService;
import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.email.core.EmailComposeStaging;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("EmailCompose2Action")
class EmailCompose2ActionUnitTest extends CarlosUnitTestBase {

    private EmailComposeManager emailComposeManager;
    private MockedStatic<ServletActionContext> servletActionContext;
    private final MockHttpSession session = new MockHttpSession();
    /** The response of the last {@link #prepare(String)}. */
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        DemographicManager demographicManager = mock(DemographicManager.class);
        emailComposeManager = mock(EmailComposeManager.class);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(EmailComposeManager.class, emailComposeManager);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(PdfPreviewCapabilityService.class, mock(PdfPreviewCapabilityService.class));
        when(emailComposeManager.getEmailConsentStatus(any(), anyInt())).thenReturn(new String[]{"Consent", "Yes"});
        when(demographicManager.getDemographicFormattedName(any(), anyInt())).thenReturn("FAKE Patient");
        when(emailComposeManager.getRecipients(any(), anyInt())).thenReturn(new List<?>[]{List.of(), List.of()});
        when(emailComposeManager.getAllSenderAccounts()).thenReturn(List.of());
        when(emailComposeManager.prepareEFormAttachments(any(), any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareEDocAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareLabAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareHRMAttachments(any(), any())).thenReturn(List.of());
        when(emailComposeManager.prepareFormAttachments(any(), any(), any(), anyInt())).thenReturn(List.of());
        servletActionContext = mockStatic(ServletActionContext.class);
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    /**
     * A draft for one patient. Window "A" leaves every option off, window "B" turns every option
     * on, so a value that came from the wrong save shows up in any assertion on it.
     */
    private static EmailAttachmentSettings draft(String patient, String fdid, boolean options, String name) {
        return new EmailAttachmentSettings(fdid, patient, new String[]{"5" + patient}, new String[]{"3" + patient},
                null, null, null, true, options, options, options, options, options, null, null,
                "fake@example.com", "FAKE subject " + name, "FAKE message " + name, null, "FULL");
    }

    private static List<EmailAttachment> file(String name) {
        return List.of(new EmailAttachment("FAKE-" + name + ".pdf", "/tmp/FAKE-" + name + ".pdf", DocumentType.EFORM, 1));
    }

    /** Prepares the compose the eForm save's redirect opens, with the given draft key. */
    private MockHttpServletRequest prepare(String draftKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/email/emailComposeAction");
        request.setSession(session);
        if (draftKey != null) {
            request.addParameter(EmailComposeStaging.DRAFT_PARAMETER, draftKey);
        }
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        response = new MockHttpServletResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        String result = new EmailCompose2Action().prepareComposeEFormMailer();
        request.setAttribute("FAKE-result", result);
        return request;
    }

    private static void assertShowsWindow(MockHttpServletRequest request, String patient, String fdid,
            boolean options, String name) {
        assertThat(request.getAttribute("FAKE-result")).isEqualTo("compose");
        assertThat(request.getAttribute("demographicId")).isEqualTo(patient);
        assertThat(request.getAttribute("fdid")).isEqualTo(fdid);
        assertThat(request.getAttribute("subjectEmail")).isEqualTo("FAKE subject " + name);
        assertThat(request.getAttribute("bodyEmail")).isEqualTo("FAKE message " + name);
        assertThat(request.getAttribute("openEFormAfterEmail")).isEqualTo(options);
        assertThat(request.getAttribute("deleteEFormAfterEmail")).isEqualTo(options);
        assertThat(request.getAttribute("isEmailAutoSend")).isEqualTo(options);
        assertThat(request.getAttribute("isEmailEncrypted")).isEqualTo(options);
        assertThat(request.getAttribute("isEmailAttachmentEncrypted")).isEqualTo(options);
    }

    @Test
    @DisplayName("should open each window's own email when both saves happen before either compose")
    void shouldOpenEachWindowsOwnDraft_whenBothSavesPrecedeBothComposes() throws Exception {
        String keyA = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        String keyB = EmailComposeStaging.stage(session, "40002", draft("10002", "20002", true, "B"));

        MockHttpServletRequest windowA = prepare(keyA);
        MockHttpServletRequest windowB = prepare(keyB);

        assertShowsWindow(windowA, "10001", "20001", false, "A");
        assertShowsWindow(windowB, "10002", "20002", true, "B");
        verify(emailComposeManager).prepareEFormAttachments(any(), eq("20001"), eq(new String[]{"510001"}));
        verify(emailComposeManager).prepareEFormAttachments(any(), eq("20002"), eq(new String[]{"510002"}));
    }

    @Test
    @DisplayName("should keep every value of the window's own save when another save lands while its PDFs render")
    void shouldKeepWholeDraft_whenAnotherSaveLandsDuringAttachmentRendering() throws Exception {
        String keyA = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        String[] keyB = new String[1];
        when(emailComposeManager.prepareEFormAttachments(any(), eq("20001"), any())).thenAnswer(invocation -> {
            // Window B saves its eForm while window A's PDFs are being rendered.
            keyB[0] = EmailComposeStaging.stage(session, "40002", draft("10002", "20002", true, "B"));
            return file("A");
        });

        MockHttpServletRequest windowA = prepare(keyA);

        // fdid, delete-after, open-after, auto-send and both encryption options: all window A's.
        assertShowsWindow(windowA, "10001", "20001", false, "A");
        assertThat(windowA.getAttribute("fid")).isEqualTo("40001");
        // Window B's draft is untouched and opens B's own compose.
        assertShowsWindow(prepare(keyB[0]), "10002", "20002", true, "B");
    }

    @Test
    @DisplayName("should show the draft-expired page with 410, never another window's email, for a missing, unknown or reused key")
    void shouldShowDraftExpired_whenKeyMissingUnknownOrReused() throws Exception {
        String keyA = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        EmailComposeStaging.stage(session, "40002", draft("10002", "20002", true, "B"));
        assertThat(prepare(keyA).getAttribute("FAKE-result")).isEqualTo("compose");

        // Missing, malformed, unknown (well-formed but never staged), and reused (window A's key again).
        for (String key : new String[]{null, "not-a-key", "AAAAAAAAAAAAAAAAAAAAAA", keyA}) {
            assertShowsDraftExpired(prepare(key), String.valueOf(key));
        }
        verify(emailComposeManager, never()).prepareEFormAttachments(any(), eq("20002"), any());
    }

    @Test
    @DisplayName("should show the draft-expired page with 410 when newer saves dropped the window's draft")
    void shouldShowDraftExpired_whenDraftWasDroppedForNewerSaves() throws Exception {
        String oldest = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        for (int i = 0; i < EmailComposeStaging.MAX_DRAFTS; i++) {
            EmailComposeStaging.stage(session, "40002", draft("10002", "2100" + i, true, "B" + i));
        }

        assertShowsDraftExpired(prepare(oldest), "dropped");
        verify(emailComposeManager, never()).prepareEFormAttachments(any(), eq("20001"), any());
    }

    /** The draft-expired page: its own result, 410, and nothing about any patient, eForm or draft. */
    private void assertShowsDraftExpired(MockHttpServletRequest request, String description) {
        assertThat(request.getAttribute("FAKE-result")).as(description).isEqualTo(EmailCompose2Action.DRAFT_EXPIRED_RESULT);
        assertThat(response.getStatus()).as(description).isEqualTo(410);
        assertThat(request.getAttribute("errorMessage")).as("no eForm error text").isNull();
        assertThat(request.getAttribute("demographicId")).as("no patient shown").isNull();
        assertThat(request.getAttribute("fdid")).as("no eForm shown").isNull();
        assertThat(request.getAttribute("bodyEmail")).as("no message shown").isNull();
    }

    @Test
    @DisplayName("should give the window its draft back when preparing its attachments fails, so a refresh retries")
    void shouldRestoreDraft_whenAttachmentPreparationFails() throws Exception {
        String keyA = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        when(emailComposeManager.prepareEFormAttachments(any(), eq("20001"), any()))
                .thenThrow(new PDFGenerationException("FAKE render failure"))
                .thenReturn(file("A"));

        assertThat(prepare(keyA).getAttribute("FAKE-result")).isEqualTo("eFormError");
        assertThat(response.getStatus()).as("a failed preparation is not an expired draft").isEqualTo(200);
        assertShowsWindow(prepare(keyA), "10001", "20001", false, "A");
    }

    @Test
    @DisplayName("should ignore and clear compose fields an older version left in the session")
    void shouldIgnoreAndClearOldSessionFields_whenDraftIsTaken() throws Exception {
        String keyA = EmailComposeStaging.stage(session, "40001", draft("10001", "20001", false, "A"));
        // Another patient's values under the names versions before #4101 used.
        session.setAttribute("demographicId", "10009");
        session.setAttribute("fdid", "20009");
        session.setAttribute("bodyEmail", "FAKE message OLD");
        session.setAttribute("isEmailAutoSend", true);
        session.setAttribute("deleteEFormAfterEmail", true);

        assertShowsWindow(prepare(keyA), "10001", "20001", false, "A");
        for (String name : new String[]{"demographicId", "fdid", "bodyEmail", "isEmailAutoSend", "deleteEFormAfterEmail"}) {
            assertThat(session.getAttribute(name)).as(name).isNull();
        }
    }

    @Test
    @DisplayName("should show the draft-expired page for a draft without a usable patient number")
    void shouldShowDraftExpired_whenDraftHasNoUsablePatientNumber() throws Exception {
        for (String patient : new String[]{null, "", "FAKE", " 10001"}) {
            String key = EmailComposeStaging.stage(session, "40001", draft(patient, "20001", false, "A"));
            assertShowsDraftExpired(prepare(key), String.valueOf(patient));
        }
        verify(emailComposeManager, never()).getRecipients(any(), anyInt());
    }

    @Test
    @DisplayName("should sanitize fid before logging invalid value")
    void shouldSanitizeFid_whenInvalidValueIsLogged() throws Exception {
        EmailAttachmentSettings settings = new EmailAttachmentSettings("20001", "123", null, null, null, null, null,
                false, false, true, true, false, false, "existing-password", "existing-clue",
                null, null, null, null, null);
        String key = EmailComposeStaging.stage(session, "abc\r\nforged-fid", settings);

        try (LogCapture capture = LogCapture.forLogger(EmailCompose2Action.class)) {
            MockHttpServletRequest request = prepare(key);

            assertThat(request.getAttribute("FAKE-result")).isEqualTo("compose");
            assertThat(request.getAttribute("fid")).isNull();
            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Invalid fid parameter received"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("abc\\r\\nforged-fid");
        }
    }
}
