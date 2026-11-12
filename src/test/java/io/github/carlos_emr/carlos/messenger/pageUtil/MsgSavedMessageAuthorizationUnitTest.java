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
import io.github.carlos_emr.carlos.commn.dao.MessageTblDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A messaging module privilege must not expose another provider's saved message through
 * its detail page or a reconstructed reply/forward, or mutate its patient links/read state.
 * Exercises the real sender/recipient lookup while mocking the persistence boundary.
 *
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("security")
@Tag("messenger")
class MsgSavedMessageAuthorizationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao mappings;
    private MessageTbl message;
    private MessageListDao recipients;
    private MessagingManager messaging;
    private MessengerDemographicManager demographics;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loginContext;

    @BeforeEach
    void prepare() {
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("101");
        security = createAndRegisterMock(SecurityInfoManager.class);
        mappings = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.MsgDemoMapDao.class);
        when(security.hasPrivilege(eq(login), eq("_msg"), eq("r"), isNull())).thenReturn(true);
        var messages = createAndRegisterMock(MessageTblDao.class);
        recipients = createAndRegisterMock(MessageListDao.class);
        createAndRegisterMock(OscarCommLocationsDao.class);
        messaging = createAndRegisterMock(MessagingManager.class);
        demographics = createAndRegisterMock(MessengerDemographicManager.class);

        message = new MessageTbl();
        message.setId(100);
        message.setSentByNo("202");
        message.setSentBy("FAKE other sender");
        message.setSubject("FAKE confidential subject");
        message.setMessage("FAKE confidential body");
        message.setDate(new Date());
        message.setTime(new Date());
        when(messages.find(100)).thenReturn(message);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(login);
    }

    @AfterEach
    void closeContexts() {
        loginContext.close();
        servletContext.close();
    }

    @ParameterizedTest
    @CsvSource({"view,GET", "view,POST", "reply,GET", "reply,POST", "replyAll,GET", "replyAll,POST",
            "forward,GET", "forward,POST", "delete,POST"})
    void shouldRefuseSavedMessage_whenProviderIsNeitherSenderNorRecipient(String operation, String method)
            throws Exception {
        request.setMethod(method);
        // A forged patient-link POST must not write or even load the linked patient names.
        if ("view".equals(operation) && "POST".equals(method)) {
            request.addParameter("linkMsgDemo", "true");
            request.addParameter("demographic_no", "42");
        }

        assertThat(execute(operation)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("viewMessageMessage")).isNull();
        assertThat(request.getAttribute("ReText")).isNull();
        assertThat(request.getAttribute("attachedDemographics")).isNull();
        assertThat(request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isNull();
        verifyNoInteractions(messaging, demographics);
    }

    @ParameterizedTest
    @CsvSource({"view,sender", "view,recipient", "reply,sender", "reply,recipient",
            "replyAll,sender", "replyAll,recipient", "forward,sender", "forward,recipient"})
    void shouldRenderSavedMessage_whenProviderIsLocalSenderOrRecipient(String operation, String role)
            throws Exception {
        request.setMethod("GET");
        if ("sender".equals(role)) {
            message.setSentByNo("101");
        } else {
            when(recipients.findByProviderNoAndMessageNo("101", 100L))
                    .thenReturn(List.of(new MessageList()));
        }
        when(messaging.getMessage(login, 100)).thenReturn(message);
        var shown = new MsgDisplayMessage();
        shown.setMessageId("100");
        shown.setMessageBody(message.getMessage());
        when(messaging.getInboxMessage(login, 100)).thenReturn(shown);

        assertThat(execute(operation)).isEqualTo("view".equals(operation) ? ActionSupport.SUCCESS : "reply");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat((String) request.getAttribute("view".equals(operation) ? "viewMessageMessage" : "ReText"))
                .contains("FAKE confidential body");
    }

    @ParameterizedTest
    @CsvSource({"view", "reply", "replyAll", "forward"})
    void shouldRenderSelectedPatientOnly_whenSharedMessageIsLinkedToAccessibleChart(String operation) throws Exception {
        allowSharedChart();
        request.setMethod("GET");
        request.addParameter("chartPatientNo", "42");
        when(messaging.getMessage(login, 100)).thenReturn(message);
        var shown = new MsgDisplayMessage();
        shown.setMessageId("100");
        shown.setMessageBody(message.getMessage());
        when(messaging.getInboxMessage(login, 100)).thenReturn(shown);
        var selected = mock(io.github.carlos_emr.carlos.commn.model.Demographic.class);
        when(selected.getFormattedName()).thenReturn("FAKE selected patient");
        when(createAndRegisterMock(io.github.carlos_emr.carlos.managers.DemographicManager.class)
                .getDemographic(login, 42)).thenReturn(selected);

        assertThat(execute(operation)).isEqualTo("view".equals(operation) ? ActionSupport.SUCCESS : "reply");

        assertThat(request.getAttribute("chartPatientNo")).isEqualTo("42");
        assertThat(request.getAttribute("demographic_no")).isEqualTo("42");
        if ("view".equals(operation)) {
            assertThat(request.getAttribute("attachedDemographics"))
                    .isEqualTo(java.util.Map.of(42, "FAKE selected patient"));
            verifyNoInteractions(messaging, demographics);
        } else {
            assertThat((String) request.getAttribute("ReText")).contains("FAKE confidential body");
            verifyNoInteractions(demographics);
        }
    }

    @ParameterizedTest
    @CsvSource({"view,unlinked", "reply,unlinked", "forward,record", "view,record",
            "replyAll,demographic", "view,demographic", "forward,messaging", "view,messaging"})
    void shouldRefuseSharedMessage_whenPatientContextDoesNotAuthorizeIt(String operation, String denial) throws Exception {
        allowSharedChart();
        request.setMethod("GET");
        request.addParameter("chartPatientNo", "42");
        switch (denial) {
            case "unlinked" -> when(mappings.findByMessageId(100)).thenReturn(List.of());
            case "record" -> when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(false);
            case "demographic" -> when(security.hasPrivilege(login, "_demographic", "r", "42")).thenReturn(false);
            case "messaging" -> when(security.hasPrivilege(login, "_msg", "r", "42")).thenReturn(false);
            default -> throw new AssertionError(denial);
        }

        assertThat(execute(operation)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(messaging, demographics);
    }

    @ParameterizedTest
    @CsvSource({"view", "delete"})
    void shouldRefuseMutation_whenOnlySharedChartReadIsAuthorized(String operation) throws Exception {
        allowSharedChart();
        request.setMethod("POST");
        request.addParameter("chartPatientNo", "42");
        request.addParameter("linkMsgDemo", "true");
        request.addParameter("demographic_no", "99");

        assertThat(execute(operation)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(messaging, demographics);
    }

    @ParameterizedTest
    @CsvSource({"dedicated", "generic"})
    void shouldRefuseChartList_whenPatientRecordAccessIsDenied(String route) throws Exception {
        allowSharedChart();
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(false);
        request.addParameter("demographic_no", "42");
        request.addParameter("boxType", "3");
        var bean = new MsgSessionBean();
        bean.setProviderNo("101");
        request.getSession().setAttribute("msgSessionBean", bean);

        String result = "dedicated".equals(route) ? new MsgDisplayDemographicMessages2Action().execute()
                : new MsgDisplayMessages2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(messaging, demographics);
    }

    @ParameterizedTest
    @CsvSource({"GET", "HEAD", "POST"})
    void shouldRefuseUnlinkSelection_whenNotPostedOrAnyMessageIsUnowned(String method) throws Exception {
        allowSharedChart();
        request.setMethod(method);
        request.addParameter("demographic_no", "42");
        when(security.hasPrivilege(login, "_msg", "w", null)).thenReturn(true);
        message.setSentByNo("101");
        var bean = new MsgSessionBean();
        bean.setProviderNo("101");
        request.getSession().setAttribute("msgSessionBean", bean);
        var action = new MsgDisplayDemographicMessages2Action();
        action.setMessageNo(new String[]{"100", "999"});

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo("POST".equals(method) ? 404 : 405);
        verifyNoInteractions(messaging, demographics);
        verify(mappings, never()).remove(anyInt(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldRequireWritePrivilege_whenUnlinkingOwnedMessage(boolean writable) throws Exception {
        allowSharedChart();
        request.setMethod("POST");
        request.addParameter("demographic_no", "42");
        when(security.hasPrivilege(login, "_msg", "w", null)).thenReturn(writable);
        message.setSentByNo("101");
        var bean = new MsgSessionBean();
        bean.setProviderNo("101");
        request.getSession().setAttribute("msgSessionBean", bean);
        var action = new MsgDisplayDemographicMessages2Action();
        action.setMessageNo(new String[]{"100"});

        if (writable) {
            assertThat(action.execute()).isEqualTo("demoMsg");
            verify(mappings).remove(100, 42);
        } else {
            org.assertj.core.api.Assertions.assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            verify(mappings, never()).remove(anyInt(), anyInt());
        }
    }

    private void allowSharedChart() {
        var link = new io.github.carlos_emr.carlos.commn.model.MsgDemoMap();
        link.setMessageID(100);
        link.setDemographic_no(42);
        when(mappings.findByMessageId(100)).thenReturn(List.of(link));
        when(security.hasPrivilege(login, "_msg", "r", "42")).thenReturn(true);
        when(security.hasPrivilege(login, "_demographic", "r", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(true);
    }

    private String execute(String operation) throws Exception {
        if ("view".equals(operation)) {
            request.addParameter("messageID", "100");
            return new MsgViewMessage2Action().execute();
        }
        request.addParameter(operation, "1");
        var action = new MsgHandleMessages2Action();
        action.setMessageNo("100");
        return action.execute();
    }
}
