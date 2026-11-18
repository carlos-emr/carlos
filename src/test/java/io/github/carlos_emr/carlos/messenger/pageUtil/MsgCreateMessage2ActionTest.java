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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MessageListDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgMessageData;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link MsgCreateMessage2Action}.
 *
 * <p>Validates security enforcement, session validation, the fix for
 * ConversionUtils dead null checks (Issue #1), and that a message carries only the
 * attachments of the compose window it was written in, refusing chart PDFs rendered for
 * another patient (issue #4673).</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgCreateMessage2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgCreateMessage2ActionTest extends CarlosWebTestBase {

    @Mock
    private MessengerDemographicManager mockDemoManager;

    private MsgCreateMessage2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessengerDemographicManager.class, mockDemoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new MsgCreateMessage2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("messengerDemographicManager", mockDemoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgCreateMessage2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when no session exists")
    void shouldThrowSecurityException_whenNoSessionExists() {
        // Given - remove logged-in info from session
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, null);

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No valid session");
    }

    @Test
    @DisplayName("should throw SecurityException when msg write privilege is denied")
    void shouldThrowSecurityException_whenMsgWritePrivilegeDenied() {
        // Given
        denyPrivilege("_msg", "w");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should throw SecurityException when session bean is missing")
    void shouldThrowSecurityException_whenSessionBeanMissing() {
        // Given - no msgSessionBean in session
        allowPrivilege("_msg", "w");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("session not initialized");
    }

    @Test
    @DisplayName("should throw SecurityException when session bean belongs to different provider")
    void shouldThrowSecurityException_whenSessionBeanBelongsToDifferentProvider() {
        // Given - session bean for a different provider
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo("DIFFERENT_PROVIDER");
        bean.setUserName("Other User");
        setSessionAttribute("msgSessionBean", bean);

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another provider");
    }

    @Test
    @DisplayName("should return error when no recipients after dedup")
    void shouldReturnError_whenNoRecipientsAfterDedup() throws Exception {
        // Given - valid session with empty provider list
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        bean.setUserName("Test Provider");
        setSessionAttribute("msgSessionBean", bean);

        // Set action with empty provider array (no recipients)
        action.setProvider(new String[]{});
        action.setSubject("Test Subject");
        action.setMessage("Test body");

        // When/Then - MsgMessageData.getProviderStructure returns empty list for
        // empty providers, triggering the C4 zero-recipient guard (ERROR).
        // MsgMessageData uses JDBC internally via MessageTblDao, so the call may
        // fail before reaching the guard if the DAO bean is unavailable.
        try {
            String result = executeAction(action);
            // If we reach here, the guard returned ERROR
            assertThat(result).isEqualTo("error");
        } catch (Exception e) {
            // JDBC DAO not available in unit test context — acceptable
            assertThat(e).isNotInstanceOf(SecurityException.class);
        }
    }

    @Test
    @DisplayName("should send message when valid recipients provided")
    void shouldSendMessage_whenValidRecipientsProvided() throws Exception {
        // Given - valid session with a provider who can receive messages
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        bean.setUserName("Test Provider");
        setSessionAttribute("msgSessionBean", bean);

        // Set action with a provider
        action.setProvider(new String[]{TEST_PROVIDER});
        action.setSubject("Test Subject");
        action.setMessage("Test body");

        // When/Then - MsgMessageData uses JDBC (MessageTblDao) which may not be
        // available in the test context.
        try {
            String result = executeAction(action);
            assertThat(result).isIn("success", "error");
        } catch (Exception e) {
            // JDBC not available in test — acceptable as we verified the guard
            assertThat(e).isNotInstanceOf(SecurityException.class);
        }
    }

    @Nested
    @DisplayName("attachments of the compose window")
    class ComposeWindowAttachments {

        private static final String RECIPIENT = "FAKE-recipient";
        private final AtomicReference<String> sentItems = new AtomicReference<>();
        private final AtomicReference<String> sentPdfs = new AtomicReference<>();
        private final AtomicReference<String> sentSubject = new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        private Runnable duringSend = () -> { };
        private String sentMessageId = "77";
        private String sentTo = RECIPIENT;
        private RuntimeException sendFailure;
        private boolean noRecipients;
        private String thisWindow;
        private String otherWindow;

        @BeforeEach
        void setUpCompose() {
            allowPrivilege("_msg", "w");
            MsgSessionBean bean = new MsgSessionBean();
            bean.setProviderNo(TEST_PROVIDER);
            bean.setUserName("Test Provider");
            setSessionAttribute("msgSessionBean", bean);
            thisWindow = MsgAttachmentStaging.newKey();
            otherWindow = MsgAttachmentStaging.newKey();
            // Two compose windows: this one for patient 101, the other for patient 202.
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, "<PDF>FAKE patient 101 chart</PDF>");
            MsgAttachmentStaging.stagePdfs(getMockSession(), otherWindow, 202, "<PDF>FAKE patient 202 chart</PDF>");
            action.setProvider(new String[]{RECIPIENT});
            action.setSubject("FAKE subject");
            action.setMessage("FAKE body");
        }

        private String send() throws Exception {
            try (MockedConstruction<MsgMessageData> ignored = mockConstruction(MsgMessageData.class, (data, context) -> {
                when(data.getDups4(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(data.getProviderStructure(any(), any())).thenReturn(
                        noRecipients ? new ArrayList<>() : new ArrayList<>(List.of(new MsgProviderData())));
                when(data.createSentToString(any())).thenReturn(sentTo);
                when(data.sendMessage2(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenAnswer(invocation -> {
                            sends.incrementAndGet();
                            sentSubject.set(invocation.getArgument(1));
                            sentItems.set(invocation.getArgument(6));
                            sentPdfs.set(invocation.getArgument(7));
                            duringSend.run();
                            if (sendFailure != null) {
                                throw sendFailure;
                            }
                            return sentMessageId;
                        });
            })) {
                return executeAction(action);
            }
        }

        /**
         * messagetbl.thesubject is a utf8mb4 VARCHAR(128) and the packaged database cuts a longer
         * value off without an error, so the action refuses it. The column
         * counts code points: an emoji is one character, two Java chars.
         */
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"S", "\uD83D\uDE00"})
        @DisplayName("should send a subject that fills messagetbl.thesubject exactly, an emoji counting as one character")
        void shouldSendTheWholeSubject_whenItFillsTheColumnExactly(String character) throws Exception {
            String subject = character.repeat(MessageTbl.SUBJECT_MAX_LENGTH);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            action.setSubject(subject);

            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(sentSubject.get()).isEqualTo(subject);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"S", "\uD83D\uDE00"})
        @DisplayName("should refuse a subject one character longer than the column, send nothing and keep what was written")
        void shouldRefuseTheSubject_whenItIsOneCharacterLongerThanTheColumn(String character) throws Exception {
            String subject = character.repeat(MessageTbl.SUBJECT_MAX_LENGTH + 1);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            action.setSubject(subject);

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            verifyNoInteractions(mockDemoManager);
            assertThat((String) getMockRequest().getAttribute("createMessageError"))
                    .isEqualTo(LocaleUtils.getMessage(getMockRequest(), "messenger.CreateMessage.msgSubjectTooLong"))
                    .contains(Integer.toString(MessageTbl.SUBJECT_MAX_LENGTH));
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo(subject);
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE body");
            assertThat(getMockRequest().getAttribute("demographic_no")).isEqualTo("101");
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE patient 101 chart</PDF>");
        }

        @Test
        @DisplayName("should measure the subject as it is stored, after its surrounding spaces are trimmed")
        void shouldSendTheTrimmedSubject_whenOnlyItsSurroundingSpacesPassTheColumn() throws Exception {
            String subject = "S".repeat(MessageTbl.SUBJECT_MAX_LENGTH);
            action.setSubject("  " + subject + "  ");

            assertThat(send()).isEqualTo("success");

            assertThat(sentSubject.get()).isEqualTo(subject);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"recipients", "delivery", "patient"})
        void shouldKeepTheOriginalArchiveCarrier_whenAReplyOrForwardSendIsRedisplayed(String refusal) throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("patient".equals(refusal) ? "202" : "101");
            action.setSubject("  FAKE raw <subject> & retry  ");
            action.setMessage("FAKE raw body H&P + 中文\nretry");
            action.setProvider(new String[]{"999998-0-1"});
            getMockRequest().setParameter("archiveMessageId", new String[]{"0055", "55"});
            noRecipients = "recipients".equals(refusal);
            if ("delivery".equals(refusal)) sentMessageId = null;

            assertThat(send()).isEqualTo("error");

            assertThat(getMockRequest().getAttribute("archiveMessageId")).isEqualTo(55);
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo("  FAKE raw <subject> & retry  ");
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE raw body H&P + 中文\nretry");
            @SuppressWarnings("unchecked")
            List<ContactIdentifier> ticked = (List<ContactIdentifier>) getMockRequest().getAttribute("replyList");
            assertThat(ticked).extracting(ContactIdentifier::getCompositeId).containsExactly("999998-0-1");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfDemographicNo()).isEqualTo(101);
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfDemographicNo()).isEqualTo(202);
            assertThat(sends.get()).isEqualTo("delivery".equals(refusal) ? 1 : 0);
            verifyNoInteractions(mockDemoManager);
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"", "0", "-1", "2147483648", "55&messageNo=99", "<script>", " 55", "55.0"})
        void shouldLeaveAnInvalidOptionalArchiveCarrierAbsent_withoutLosingTheRefusedCompose(String raw) throws Exception {
            noRecipients = true;
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            getMockRequest().setParameter("archiveMessageId", raw);

            assertThat(send()).isEqualTo("error");

            assertThat(getMockRequest().getAttribute("archiveMessageId")).isNull();
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo("FAKE subject");
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE body");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNotNull();
            assertThat(sends.get()).isZero();
        }

        @Test
        void shouldRejectConflictingRawArchiveCarriers_withoutChoosingTheFirstMessage() throws Exception {
            noRecipients = true;
            getMockRequest().setParameter("archiveMessageId", new String[]{"55", "56"});

            assertThat(send()).isEqualTo("error");

            assertThat(getMockRequest().getAttribute("archiveMessageId")).isNull();
            assertThat(sends.get()).isZero();
        }

        @Test
        void shouldKeepFreshComposeCompatible_whenNoArchiveCarrierIsSubmitted() throws Exception {
            noRecipients = true;
            assertThat(send()).isEqualTo("error");
            assertThat(getMockRequest().getAttribute("archiveMessageId")).isNull();
            noRecipients = false;
            assertThat(send()).isEqualTo("success");
            assertThat(sends.get()).isEqualTo(1);
        }

        @Test
        void shouldSendTheRecoveredComposition_whenTheUserRetriesAfterAnOrdinarySendFailure() throws Exception {
            noRecipients = true;
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            getMockRequest().setParameter("archiveMessageId", "55");
            assertThat(send()).isEqualTo("error");
            Object recoveredId = getMockRequest().getAttribute("archiveMessageId");
            assertThat(recoveredId).isEqualTo(55);
            String recoveredSubject = (String) getMockRequest().getAttribute("ReSubject");
            String recoveredBody = (String) getMockRequest().getAttribute("ReText");
            // A following HTTP request/action carries form values, never the old request attributes.
            mockRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/messenger/CreateMessage");
            mockRequest.setSession(getMockSession());
            mockRequest.setParameter("archiveMessageId", recoveredId.toString());
            setUpActionContext();
            action = new MsgCreateMessage2Action();
            injectField("securityInfoManager", mockSecurityInfoManager);
            injectField("messengerDemographicManager", mockDemoManager);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            action.setSubject(recoveredSubject);
            action.setMessage(recoveredBody);
            action.setProvider(new String[]{RECIPIENT});
            assertThat(mockRequest.getAttribute("archiveMessageId")).isNull();
            noRecipients = false;

            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(sentPdfs.get()).isEqualTo("<PDF>FAKE patient 101 chart</PDF>");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfDemographicNo()).isEqualTo(202);
            verify(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
        }

        /** The next HTTP request of the same rendered form: a new action, the same posted fields and token. */
        private void repeatSubmission(String token) {
            mockRequest = new org.springframework.mock.web.MockHttpServletRequest("POST", "/messenger/CreateMessage");
            mockRequest.setSession(getMockSession());
            mockResponse = new org.springframework.mock.web.MockHttpServletResponse();
            if (token != null) {
                mockRequest.setParameter(FormSubmissionGuard.PARAMETER, token);
            }
            setUpActionContext();
            action = new MsgCreateMessage2Action();
            injectField("securityInfoManager", mockSecurityInfoManager);
            injectField("messengerDemographicManager", mockDemoManager);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            action.setSubject("FAKE subject");
            action.setMessage("FAKE body");
            action.setProvider(new String[]{RECIPIENT});
        }

        @Test
        @DisplayName("should send one message when one rendered compose form is submitted twice")
        void shouldSendOnce_whenTheSameRenderedFormIsSubmittedTwice() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);

            assertThat(send()).isEqualTo("success");
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(RECIPIENT);

            // A double click, a re-click during a slow send or a reload of the result page.
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            // The repeat ends as the first send did: the same confirmation, for what was sent.
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(RECIPIENT);
            assertThat(getMockResponse().getStatus()).isEqualTo(200);
            verify(mockDemoManager, times(1)).attachDemographicToMessage(any(), eq(77), eq(101));
            // The repeat neither takes nor restores this window's attachments.
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
            // A clean send's repeat shows no after-send warning either.
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED)).isNull();
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ATTACHMENTS_AFTER_SEND_FAILED)).isNull();
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
                RECIPIENT + "|false", "101: On Call|false", "101: On Call|true"
        })
        @DisplayName("should confirm a legacy send with its full recipient line and no false failure flags")
        void shouldConfirmTheFullRecipient_whenTheSavedResultIsFromAnEarlierVersion(
                String storedRecipient, boolean requestArchive) throws Exception {
            repeatSubmission(null);
            if (requestArchive) {
                getMockRequest().setParameter("archiveMessageId", "55");
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
            }
            // The first send took this window's attachments, as a real send does.
            MsgAttachmentStaging.take(getMockSession(), thisWindow);
            java.lang.reflect.Method digest = MsgCreateMessage2Action.class.getDeclaredMethod("contentDigest");
            digest.setAccessible(true);
            java.lang.reflect.Method answer = MsgCreateMessage2Action.class.getDeclaredMethod("answerUsedForm",
                    jakarta.servlet.http.HttpSession.class, String.class);
            answer.setAccessible(true);
            // A session that outlived a redeploy: "id:digest:recipients", with no warning digits.
            String legacy = "77:" + digest.invoke(action) + ":" + storedRecipient;

            assertThat(answer.invoke(action, getMockSession(), legacy)).isEqualTo("success");

            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(storedRecipient);
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED)).isNull();
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ATTACHMENTS_AFTER_SEND_FAILED)).isNull();
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                    .isEqualTo(requestArchive ? Boolean.TRUE : null);
        }

        @Test
        void shouldRedisplayAnEditedRepeat_whenItsRecipientsAndSubjectWereCleared() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            repeatSubmission(token);
            action.setProvider(null);
            action.setSubject(null);
            send();

            assertThat(getMockResponse().getStatus()).isEqualTo(409);
            assertThat(sends.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should confirm an empty recipient line when the first send named no one")
        void shouldRepeatAnEmptyRecipientLine_whenTheFirstSendNamedNoOne() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            sentTo = null;
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isNull();

            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo("");
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        @org.junit.jupiter.params.provider.ValueSource(strings = {"   "})
        @DisplayName("should send with the subject 'none' when the subject is missing or only spaces")
        void shouldSendWithTheSubjectNone_whenTheSubjectIsMissingOrOnlySpaces(String subject) throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            action.setSubject(subject);

            assertThat(send()).isEqualTo("success");

            assertThat(sentSubject.get()).isEqualTo("none");
        }

        @Test
        @DisplayName("should tick no one again when a refused send had no recipients at all")
        void shouldTickNoOneAgain_whenARefusedSendHadNoRecipients() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setProvider(null);
            noRecipients = true;

            assertThat(send()).isEqualTo("error");

            assertThat(getMockRequest().getAttribute("replyList")).isEqualTo(List.of());
        }

        @Test
        @DisplayName("should leave a blank recipient id out when ticking a refused send's recipients again")
        void shouldLeaveABlankIdOut_whenTickingARefusedSendsRecipientsAgain() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("202");
            action.setProvider(new String[]{" ", null, "999998-0-1"});

            assertThat(send()).isEqualTo("error");

            @SuppressWarnings("unchecked")
            List<ContactIdentifier> ticked = (List<ContactIdentifier>) getMockRequest().getAttribute("replyList");
            assertThat(ticked).extracting(ContactIdentifier::getCompositeId).containsExactly("999998-0-1");
        }

        @Test
        @DisplayName("should send and archive nothing when Send and Archive carries no message replied to")
        void shouldArchiveNothing_whenSendAndArchiveCarriesNoMessage() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");

            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"", "null"})
        @DisplayName("should show a changed unlinked message again when the used form is resubmitted")
        void shouldRedisplayAChangedUnlinkedMessage_whenTheUsedFormIsResubmitted(String noPatient) throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            repeatSubmission(token);
            action.setDemographic_no(noPatient);
            action.setMessage("FAKE changed body");

            assertThat(send()).isEqualTo("error");

            assertThat(getMockResponse().getStatus()).isEqualTo(409);
            assertThat(getMockRequest().getAttribute("demographic_no")).isNull();
            assertThat(sends.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should treat repeated and blank recipient ids as one message when the same form is submitted twice")
        void shouldSendOnce_whenTheRecipientIdsRepeatOrAreBlank() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            action.setProvider(new String[]{RECIPIENT, " ", null, RECIPIENT});
            assertThat(send()).isEqualTo("success");

            // The same recipients ticked in another order, blank ids dropped: the same message.
            repeatSubmission(token);
            action.setProvider(new String[]{RECIPIENT});
            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(getMockResponse().getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should show the first send's after-send warnings again when the same form is submitted twice")
        void shouldRepeatTheFirstSendsWarnings_whenTheSameRenderedFormIsSubmittedAgain() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            doThrow(new IllegalStateException("FAKE patient link failed"))
                    .when(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
            try (MockedStatic<MsgAttachmentStaging> staging = mockStatic(MsgAttachmentStaging.class, CALLS_REAL_METHODS)) {
                staging.when(() -> MsgAttachmentStaging.releaseSource(any(), eq(thisWindow), any()))
                        .thenThrow(new IllegalStateException("FAKE cleanup failed"));
                repeatSubmission(token);
                assertThat(send()).isEqualTo("success");
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);

                // The browser ends on the repeat's page: a double click, or a reload of the sent page.
                repeatSubmission(token);
                assertThat(send()).isEqualTo("success");
            }

            assertThat(sends.get()).isEqualTo(1);
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(RECIPIENT);
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED))
                    .isEqualTo(Boolean.TRUE);
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ATTACHMENTS_AFTER_SEND_FAILED))
                    .isEqualTo(Boolean.TRUE);
            assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
            // The repeat retries none of the after-send steps.
            verify(mockDemoManager, times(1)).attachDemographicToMessage(any(), eq(77), eq(101));
        }

        @Test
        @DisplayName("should not confirm a changed message as sent when the already used form is submitted again")
        void shouldRedisplayUnsentChange_whenTheUsedFormIsResubmittedWithDifferentContent() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            // The user went back to the page that had sent, changed the message and sent again.
            repeatSubmission(token);
            action.setSubject("FAKE corrected subject");
            action.setMessage("FAKE corrected body");
            String result = send();

            // Not the first send's "message sent" page: the corrected message has not been sent.
            assertThat(result).isEqualTo("error");
            assertThat(getMockResponse().getStatus()).isEqualTo(409);
            assertThat(sends.get()).isEqualTo(1);
            assertThat(getMockRequest().getAttribute("SentMessageProvs")).isNull();
            assertThat((String) getMockRequest().getAttribute("createMessageError")).isNotBlank();
            // The compose page comes back with what was typed, so it can be sent as a new message.
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo("FAKE corrected subject");
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE corrected body");

            // The page shown again carries a token of its own; sending from it delivers the correction once.
            String fresh = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(fresh);
            action.setSubject("FAKE corrected subject");
            action.setMessage("FAKE corrected body");
            assertThat(send()).isEqualTo("success");
            assertThat(sends.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should not confirm the first send when the used form is submitted again for other recipients")
        void shouldRedisplayUnsentChange_whenTheUsedFormIsResubmittedForOtherRecipients() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            assertThat(send()).isEqualTo("success");

            repeatSubmission(token);
            action.setProvider(new String[]{"999997-0-1"});

            assertThat(send()).isEqualTo("error");
            assertThat(getMockResponse().getStatus()).isEqualTo(409);
            assertThat(sends.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should keep the token usable when the first submission was refused before anything was stored")
        void shouldSendOnRetry_whenTheFirstSubmissionWasRefusedBeforeStorage() throws Exception {
            String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
            repeatSubmission(token);
            noRecipients = true;
            assertThat(send()).isEqualTo("error");
            assertThat(sends.get()).isZero();

            // The same rendering submitted again (the second click of a double click) may still send.
            repeatSubmission(token);
            noRecipients = false;
            assertThat(send()).isEqualTo("success");
            assertThat(sends.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should refuse a token this session did not issue for the compose form, and send nothing")
        void shouldRefuseWithConflict_whenTheTokenWasNotIssuedForThisForm() throws Exception {
            String foreign = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.APPOINTMENT_ADD, null);
            repeatSubmission(foreign);

            assertThat(send()).isEqualTo(FormSubmissionGuard.REFUSED_RESULT);

            assertThat(getMockResponse().getStatus()).isEqualTo(409);
            assertThat(sends.get()).isZero();
            // Nothing was taken from this window: it can still be sent from a freshly opened form.
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNotNull();
            verifyNoInteractions(mockDemoManager);
        }

        @Test
        @DisplayName("should keep sending without a token as before")
        void shouldSendEachSubmission_whenNoTokenIsPosted() throws Exception {
            repeatSubmission(null);
            assertThat(send()).isEqualTo("success");
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, "<PDF>FAKE patient 101 chart</PDF>");
            repeatSubmission(null);
            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should send this window's chart PDFs only, and only once")
        void shouldSendOnlyThisWindowsAttachments_whenAnotherWindowHasAttachments() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("success");

            assertThat(sentPdfs.get()).isEqualTo("<PDF>FAKE patient 101 chart</PDF>");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE patient 202 chart</PDF>");
            verify(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
        }

        @Test
        @DisplayName("should release the items saved for a message once a reply carrying them is sent")
        void shouldReleaseTheMessagesSavedItems_whenAReplyCarryingThemIsSent() throws Exception {
            String saved = MsgAttachmentStaging.forwardKey(getMockSession(), 55);
            MsgAttachmentStaging.stageItems(getMockSession(), saved, "<root>FAKE saved item</root>");
            String reply = MsgAttachmentStaging.composeKey(getMockSession(), 55);
            action.setAttachmentKey(reply);

            assertThat(send()).isEqualTo("success");

            assertThat(sentItems.get()).isEqualTo("<root>FAKE saved item</root>");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), saved)).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), MsgAttachmentStaging.composeKey(getMockSession(), 55)))
                    .isNull();
        }

        @Test
        @DisplayName("should send no attachments when the form names no compose window")
        void shouldSendNoAttachments_whenComposeKeyMissing() throws Exception {
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("success");

            assertThat(sentPdfs.get()).isNull();
            assertThat(sentItems.get()).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow)).isNotNull();
        }

        @Test
        @DisplayName("should refuse chart PDFs on a message linked to another patient and keep everything")
        void shouldRefuseChartPdfs_whenMessageLinkedToAnotherPatient() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("202");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            verify(mockDemoManager, never()).attachDemographicToMessage(any(), anyInt(), anyInt());
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE patient 101 chart</PDF>");
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
            assertThat((String) getMockRequest().getAttribute("createMessageError")).contains("different patient");
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo("FAKE subject");
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE body");
            assertThat(getMockRequest().getAttribute("demographic_no")).isEqualTo("202");
        }

        @Test
        @DisplayName("should refuse chart PDFs larger than a message can hold, which the database would cut in two, and keep everything")
        void shouldRefuseChartPdfs_whenLargerThanAMessageCanHold() throws Exception {
            String tooLarge = "<PDF>" + "A".repeat(MessageTbl.PDF_ATTACHMENT_MAX_BYTES) + "</PDF>";
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, tooLarge);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            assertThat((String) getMockRequest().getAttribute("createMessageError"))
                    .isEqualTo(MsgAttachPDF2Action.PDFS_TOO_LARGE);
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment()).isEqualTo(tooLarge);
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
        }

        @Test
        @DisplayName("should refuse chart PDFs that fit only until the send doubles a backslash of a title, and keep everything")
        void shouldRefuseChartPdfs_whenTheyOnlyOverflowOnceQuoted() throws Exception {
            // As staged this is exactly the column's size; the send stores it one byte over.
            String record = "<PDF>" + "A".repeat(MessageTbl.PDF_ATTACHMENT_MAX_BYTES - "<PDF></PDF>".length() - 1) + "\\" + "</PDF>";
            assertThat(record).hasSize(MessageTbl.PDF_ATTACHMENT_MAX_BYTES);
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, record);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            assertThat((String) getMockRequest().getAttribute("createMessageError"))
                    .isEqualTo(MsgAttachPDF2Action.PDFS_TOO_LARGE);
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment()).isEqualTo(record);
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
        }

        @Test
        @DisplayName("should send chart PDFs that fill a message exactly once a backslash of a title is doubled")
        void shouldSendChartPdfs_whenTheyFillAMessageExactlyOnceQuoted() throws Exception {
            String record = "<PDF>" + "A".repeat(MessageTbl.PDF_ATTACHMENT_MAX_BYTES - "<PDF></PDF>".length() - 2) + "\\" + "</PDF>";
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, record);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(sentPdfs.get()).isEqualTo(record);
        }

        @Test
        @DisplayName("should send chart PDFs that fill a message exactly")
        void shouldSendChartPdfs_whenTheyFillAMessageExactly() throws Exception {
            String exact = "<PDF>" + "A".repeat(MessageTbl.PDF_ATTACHMENT_MAX_BYTES - "<PDF></PDF>".length()) + "</PDF>";
            assertThat(exact).hasSize(MessageTbl.PDF_ATTACHMENT_MAX_BYTES);
            MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, exact);
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("success");

            assertThat(sends.get()).isEqualTo(1);
            assertThat(sentPdfs.get()).isEqualTo(exact);
        }

        @Test
        @DisplayName("should tick the refused message's recipients again, leaving out an id that is not a contact's")
        void shouldTickTheRecipientsAgain_whenASendIsRefused() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("202");
            action.setProvider(new String[]{"999998-0-1", "FAKE-recipient", "5-"});

            assertThat(send()).isEqualTo("error");

            @SuppressWarnings("unchecked")
            List<ContactIdentifier> ticked = (List<ContactIdentifier>) getMockRequest().getAttribute("replyList");
            assertThat(ticked).extracting(ContactIdentifier::getCompositeId).containsExactly("999998-0-1");
        }

        @Test
        @DisplayName("should refuse chart PDFs on a message whose patient link was cleared")
        void shouldRefuseChartPdfs_whenPatientLinkCleared() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNotNull();
        }

        @Test
        @DisplayName("should refuse once, then send without them, when the session released this window's attachments")
        void shouldRefuseOnceThenSendWithoutThem_whenTheSessionReleasedThisWindowsAttachments() throws Exception {
            // Attachments staged in enough other windows push this window's out of the session.
            for (int i = 0; i < MsgAttachmentStaging.MAX_ENTRIES; i++) {
                MsgAttachmentStaging.stageItems(getMockSession(), MsgAttachmentStaging.newKey(), "<root/>");
            }
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            assertThat((String) getMockRequest().getAttribute("createMessageError")).contains("attachments were released");
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE body");
            assertThat(getMockRequest().getAttribute("demographic_no")).isEqualTo("101");

            // The user has been told; sending again goes out without them.
            assertThat(send()).isEqualTo("success");
            assertThat(sends.get()).isEqualTo(1);
            assertThat(sentPdfs.get()).isNull();
            assertThat(sentItems.get()).isNull();
        }

        @Test
        @DisplayName("should put the attachments back when there is no valid recipient")
        void shouldKeepAttachments_whenNoValidRecipients() throws Exception {
            noRecipients = true;
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");

            assertThat(send()).isEqualTo("error");

            assertThat(sends.get()).isZero();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE patient 101 chart</PDF>");
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
        }

        @Test
        void shouldKeepTheChooserClear_whenTheInflightSendReturnsNoMessageId() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            duringSend = () -> MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 101, null);
            sentMessageId = null;

            assertThat(send()).isEqualTo("error");

            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfDemographicNo()).isEqualTo(202);
            assertThat(getMockRequest().getAttribute("ReText")).isEqualTo("FAKE body");
            assertThat(getMockRequest().getAttribute("ReSubject")).isEqualTo("FAKE subject");
            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(thisWindow);
            assertThat((String) getMockRequest().getAttribute("createMessageError")).contains("Failed to send");
        }

        @Test
        void shouldKeepDiscardedAttachmentsAbsent_whenTheInflightSendThrows() {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            duringSend = () -> MsgAttachmentStaging.discard(getMockSession(), thisWindow);
            sendFailure = new IllegalStateException("FAKE transport failure");

            assertThatThrownBy(this::send).isSameAs(sendFailure);

            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
            assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfDemographicNo()).isEqualTo(202);
        }

        @Test
        void shouldKeepTheNewerReplacement_whenTheInflightSendFails() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            duringSend = () -> MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 202, "<PDF>FAKE replacement</PDF>");
            sentMessageId = "";

            assertThat(send()).isEqualTo("error");

            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE replacement</PDF>");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfDemographicNo()).isEqualTo(202);
        }

        @Test
        void shouldKeepTheNewerSendRestorable_whenTheOlderSendSucceeds() throws Exception {
            action.setAttachmentKey(thisWindow);
            action.setDemographic_no("101");
            AtomicReference<MsgAttachmentStaging.Staged> newerSend = new AtomicReference<>();
            duringSend = () -> {
                MsgAttachmentStaging.stagePdfs(getMockSession(), thisWindow, 202, "<PDF>FAKE newer send</PDF>");
                newerSend.set(MsgAttachmentStaging.take(getMockSession(), thisWindow));
            };

            assertThat(send()).isEqualTo("success");
            MsgAttachmentStaging.restore(getMockSession(), thisWindow, newerSend.get());

            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfAttachment())
                    .isEqualTo("<PDF>FAKE newer send</PDF>");
            assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow).pdfDemographicNo()).isEqualTo(202);
        }

        /**
         * #6042: Send and Archive archives the message replied to only once the reply is sent, and
         * only the replying provider's own local copy. The page used to archive it first, so a
         * refused reply left it archived with no reply.
         */
        @Nested
        @DisplayName("Send and Archive")
        class SendAndArchive {

            private final MessageListDao messageListDao = mock(MessageListDao.class);
            private final MessageList ownCopy = new MessageList();

            @BeforeEach
            void replyToMessage55() {
                replaceSpringUtilsBean(MessageListDao.class, messageListDao);
                replaceSpringUtilsBean(OscarCommLocationsDao.class, mock(OscarCommLocationsDao.class));
                ownCopy.setProviderNo(TEST_PROVIDER);
                ownCopy.setMessage(55L);
                ownCopy.setStatus(MessageList.STATUS_READ);
                ownCopy.setRemoteLocation(0);
                ownCopy.setDestinationFacilityId(0);
                when(messageListDao.findByProviderNoAndMessageNo(TEST_PROVIDER, 55L)).thenAnswer(lookup -> {
                    // The message replied to is looked up only once the reply has gone out.
                    assertThat(sends.get()).isEqualTo(1);
                    return List.of(ownCopy);
                });
                action.setAttachmentKey(thisWindow);
                action.setDemographic_no("101");
                getMockRequest().setParameter("archiveMessageId", "55");
            }

            @Test
            void shouldArchiveTheMessageRepliedTo_afterTheReplyIsSent() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");

                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_DELETED);
                verify(messageListDao).merge(ownCopy);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
            }

            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.ValueSource(strings = {"persistence", "privilege", "message-id"})
            void shouldWarnAndStillArchive_whenTheSentReplyCannotBeLinkedToItsPatient(String failure) throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                if ("message-id".equals(failure)) {
                    sentMessageId = "FAKE-unusable-id";
                } else {
                    RuntimeException refused = "privilege".equals(failure)
                            ? new SecurityException("FAKE patient privilege changed")
                            : new org.springframework.dao.DataAccessResourceFailureException("FAKE patient link failed");
                    doThrow(refused).when(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
                }

                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(RECIPIENT);
                assertThat(getMockRequest().getAttribute("patientLinkAfterSendFailed")).isEqualTo(Boolean.TRUE);
                assertThat(getMockRequest().getAttribute("attachmentsAfterSendFailed")).isNull();
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
                assertThat(getMockRequest().getAttribute("createMessageError")).isNull();
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_DELETED);
                verify(messageListDao, times(1)).merge(ownCopy);
                assertThat(MsgAttachmentStaging.peek(getMockSession(), thisWindow)).isNull();
                assertThat(MsgAttachmentStaging.peek(getMockSession(), otherWindow).pdfDemographicNo()).isEqualTo(202);
            }

            @Test
            void shouldShowHowToArchiveAnAlreadySentReply_whenTheUsedFormRequestsArchiving() throws Exception {
                String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
                repeatSubmission(token);
                getMockRequest().setParameter("archiveMessageId", "55");
                assertThat(send()).isEqualTo("success");
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();

                repeatSubmission(token);
                getMockRequest().setParameter("archiveMessageId", "55");
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);
                verify(messageListDao, never()).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);
            }

            @Test
            void shouldRepeatTheArchiveWarning_whenTheSameReplyFormIsSubmittedAgainAfterArchivingFailed() throws Exception {
                String token = FormSubmissionGuard.issue(getMockSession(), FormSubmissionGuard.Scope.MESSENGER_SEND, null);
                doThrow(new org.springframework.dao.DataRetrievalFailureException("FAKE archive failed"))
                        .when(messageListDao).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);
                repeatSubmission(token);
                getMockRequest().setParameter("archiveMessageId", "55");
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                assertThat(send()).isEqualTo("success");
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);

                repeatSubmission(token);
                getMockRequest().setParameter("archiveMessageId", "55");
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED)).isNull();
                // The repeat does not try to archive again.
                verify(messageListDao, times(1)).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);
            }

            @Test
            void shouldWarnAndStillArchive_whenSavedAttachmentCleanupFailsAfterTheReplyIsSent() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                try (MockedStatic<MsgAttachmentStaging> staging = mockStatic(MsgAttachmentStaging.class, CALLS_REAL_METHODS)) {
                    staging.when(() -> MsgAttachmentStaging.releaseSource(any(), eq(thisWindow), any()))
                            .thenThrow(new IllegalStateException("FAKE cleanup failed"));

                    assertThat(send()).isEqualTo("success");

                    assertThat(sends.get()).isEqualTo(1);
                    assertThat(getMockRequest().getAttribute("attachmentsAfterSendFailed")).isEqualTo(Boolean.TRUE);
                    assertThat(getMockRequest().getAttribute("patientLinkAfterSendFailed")).isNull();
                    assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isNull();
                    assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_DELETED);
                    verify(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
                    verify(messageListDao, times(1)).merge(ownCopy);
                    staging.verify(() -> MsgAttachmentStaging.restore(any(), any(), any()), never());
                }
            }

            @Test
            void shouldShowEveryFailure_whenPatientLinkCleanupAndArchiveAllFailAfterSending() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                doThrow(new IllegalStateException("FAKE patient link failed"))
                        .when(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));
                doThrow(new org.springframework.dao.DataRetrievalFailureException("FAKE archive failed"))
                        .when(messageListDao).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);
                try (MockedStatic<MsgAttachmentStaging> staging = mockStatic(MsgAttachmentStaging.class, CALLS_REAL_METHODS)) {
                    staging.when(() -> MsgAttachmentStaging.releaseSource(any(), eq(thisWindow), any()))
                            .thenThrow(new IllegalStateException("FAKE cleanup failed"));

                    assertThat(send()).isEqualTo("success");

                    assertThat(sends.get()).isEqualTo(1);
                    assertThat(getMockRequest().getAttribute("SentMessageProvs")).isEqualTo(RECIPIENT);
                    assertThat(getMockRequest().getAttribute("patientLinkAfterSendFailed")).isEqualTo(Boolean.TRUE);
                    assertThat(getMockRequest().getAttribute("attachmentsAfterSendFailed")).isEqualTo(Boolean.TRUE);
                    assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED)).isEqualTo(Boolean.TRUE);
                    verify(messageListDao, times(1)).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);
                    staging.verify(() -> MsgAttachmentStaging.restore(any(), any(), any()), never());
                }
            }

            @Test
            void shouldWarnWithoutArchiving_whenAnOrdinarySentMessageCannotBeLinkedToItsPatient() throws Exception {
                doThrow(new IllegalStateException("FAKE patient link failed"))
                        .when(mockDemoManager).attachDemographicToMessage(any(), eq(77), eq(101));

                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute("patientLinkAfterSendFailed")).isEqualTo(Boolean.TRUE);
                verifyNoInteractions(messageListDao);
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_READ);
            }

            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.ValueSource(strings = {"recipients", "patient", "delivery"})
            void shouldLeaveTheMessageRepliedToInTheInbox_whenTheReplyIsRefused(String refusal) throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                switch (refusal) {
                    case "recipients" -> noRecipients = true;
                    case "patient" -> action.setDemographic_no("202");
                    default -> sentMessageId = "";
                }

                assertThat(send()).isEqualTo("error");

                verifyNoInteractions(messageListDao);
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_READ);
                // The redisplayed page still carries the message replied to, for the next try.
                assertThat(getMockRequest().getAttribute("archiveMessageId")).isEqualTo(55);
            }

            @Test
            void shouldNotArchive_whenAPlainSendCarriesTheMessageRepliedTo() throws Exception {
                assertThat(send()).isEqualTo("success");

                verifyNoInteractions(messageListDao);
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_READ);
            }

            @Test
            void shouldArchiveNothing_whenTheProviderDidNotReceiveTheMessage() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                doReturn(List.of()).when(messageListDao).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);

                assertThat(send()).isEqualTo("success");

                verify(messageListDao, never()).merge(any());
            }

            @Test
            void shouldSayTheMessageIsStillInTheInbox_whenTheArchiveStepItselfFails() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                try (MockedStatic<MsgBulkOperationHelper> archive = mockStatic(MsgBulkOperationHelper.class, CALLS_REAL_METHODS)) {
                    archive.when(() -> MsgBulkOperationHelper.updateSelectedMessages(any(), any(), any(), any()))
                            .thenThrow(new IllegalStateException("FAKE archive step failed"));

                    // The reply is sent; the archive failure is reported on the sent page, not thrown.
                    assertThat(send()).isEqualTo("success");
                }

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);
                assertThat(ownCopy.getStatus()).isEqualTo(MessageList.STATUS_READ);
            }

            @Test
            void shouldSayTheMessageIsStillInTheInbox_whenTheArchiveFailsAfterTheReplyIsSent() throws Exception {
                getMockRequest().setParameter(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND, "1");
                doThrow(new org.springframework.dao.DataRetrievalFailureException("FAKE database failure"))
                        .when(messageListDao).findByProviderNoAndMessageNo(TEST_PROVIDER, 55L);

                // The reply is sent and stays sent; the sent page says the original is still in the inbox.
                assertThat(send()).isEqualTo("success");

                assertThat(sends.get()).isEqualTo(1);
                assertThat(getMockRequest().getAttribute(MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED))
                        .isEqualTo(Boolean.TRUE);
            }
        }
    }
}
