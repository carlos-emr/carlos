/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.MsgMessageData;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class MsgCreateMessageTextUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseWithoutConsumingTheDraft_whenRequestMethodIsReadOnly(String method) throws Exception {
        exercise("Owned subject", method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "😀"})
    void shouldKeepDraftAndAttachmentsWithoutSending_whenSubjectExceedsColumn(String character) throws Exception {
        exercise(character.repeat(129), "oversized");
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", "😀"})
    void shouldSendWholeSubjectAndConsumeAttachments_whenSubjectExactlyFits(String character) throws Exception {
        exercise(character.repeat(128), "success");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-recipients", "send-failed"})
    void shouldKeepDraftAndAttachments_whenSendDoesNotSucceed(String failure) throws Exception {
        exercise("Owned subject", failure);
    }

    private void exercise(String subject, String outcome) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        boolean readOnly = "GET".equals(outcome) || "HEAD".equals(outcome);
        request.setMethod(readOnly ? outcome : "POST");
        var login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_msg", "w", null)).thenReturn(true);
        var demographics = mock(MessengerDemographicManager.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(MessengerDemographicManager.class, demographics);
        registerMock(PlatformTransactionManager.class, new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        });
        request.setParameter(MessengerSubmissionGuard.PARAMETER,
                MessengerSubmissionGuard.issue(request.getSession(), "999998"));
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo("999998");
        bean.setUserName("Owned sender");
        bean.setAttachment("owned chart attachment");
        bean.setPDFAttachment("owned PDF attachment");
        bean.setTotalAttachmentCount(2);
        bean.setSubject(subject);
        bean.setMessage("**Owned** body &amp; 😀");
        request.getSession().setAttribute("msgSessionBean", bean);
        String[] recipients = {"999998-0"};
        try (var servlet = mockStatic(ServletActionContext.class);
             var loggedIn = mockStatic(LoggedInInfo.class);
             var sends = mockConstruction(MsgMessageData.class, (sender, construction) -> {
                 when(sender.getDups4(any())).thenReturn(recipients);
                 when(sender.getProviderStructure(eq(login), any())).thenReturn("no-recipients".equals(outcome)
                         ? new ArrayList<>() : new ArrayList<>(List.of(mock(MsgProviderData.class))));
                 when(sender.createSentToString(any())).thenReturn("Owned recipient");
                 when(sender.sendMessage2(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                         .thenReturn("send-failed".equals(outcome) ? null : "123");
             })) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
            MsgCreateMessage2Action action = new MsgCreateMessage2Action();
            action.setSubject(subject);
            action.setMessage(bean.getMessage());
            action.setProvider(recipients);
            action.setDemographic_no("1001");
            String result = action.execute();

            if ("success".equals(outcome)) {
                assertThat(result).isEqualTo("success");
                verify(sends.constructed().getFirst()).sendMessage2(eq("**Owned** body &amp; 😀"), eq(subject),
                        eq("Owned sender"), eq("Owned recipient"), eq("999998"), any(),
                        eq("owned chart attachment"), eq("owned PDF attachment"), any());
                verify(demographics).attachDemographicToMessage(login, 123, 1001);
                assertThat(bean.getAttachment()).isNull();
                assertThat(bean.getPDFAttachment()).isNull();
                assertThat(bean.getMessage()).isNull();
                assertThat(bean.getSubject()).isNull();
                assertThat(bean.getTotalAttachmentCount()).isZero();
            } else {
                assertThat(result).isEqualTo(readOnly ? "none" : "error");
                if (!readOnly) {
                    assertThat(request.getAttribute("ReSubject")).isEqualTo(subject);
                    assertThat(request.getAttribute("ReText")).isEqualTo("**Owned** body &amp; 😀");
                    assertThat(request.getAttribute("demographic_no")).isEqualTo("1001");
                    assertThat(request.getAttribute("rejectedRecipientIds")).isEqualTo(Set.of("999998-0"));
                }
                assertThat(bean.getAttachment()).isEqualTo("owned chart attachment");
                assertThat(bean.getPDFAttachment()).isEqualTo("owned PDF attachment");
                assertThat(bean.getMessage()).isEqualTo("**Owned** body &amp; 😀");
                assertThat(bean.getSubject()).isEqualTo(subject);
                assertThat(bean.getTotalAttachmentCount()).isEqualTo(2);
                verifyNoInteractions(demographics);
                if (readOnly) {
                    assertThat(response.getStatus()).isEqualTo(405);
                    assertThat(response.getHeader("Allow")).isEqualTo("POST");
                    assertThat(sends.constructed()).isEmpty();
                    verifyNoInteractions(security);
                } else if ("oversized".equals(outcome)) {
                    assertThat(response.getStatus()).isEqualTo(400);
                    assertThat(request.getAttribute("createMessageError").toString()).contains("maximum length", "128");
                    assertThat(sends.constructed()).isEmpty();
                } else if ("no-recipients".equals(outcome)) {
                    verify(sends.constructed().getFirst(), never()).sendMessage2(any(), any(), any(), any(), any(), any(), any(), any(), any());
                }
            }
        }
    }
}
