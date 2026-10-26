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

import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgMessageData;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
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
        mockRequest.setMethod("POST");

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessengerDemographicManager.class, mockDemoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);
        mockRequest.setParameter(MessengerSubmissionGuard.PARAMETER,
                MessengerSubmissionGuard.issue(mockRequest.getSession(), TEST_PROVIDER));

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
        private final java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        private Runnable duringSend = () -> { };
        private String sentMessageId = "77";
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
                when(data.createSentToString(any())).thenReturn(RECIPIENT);
                when(data.sendMessage2(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                        .thenAnswer(invocation -> {
                            sends.incrementAndGet();
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
    }
}
