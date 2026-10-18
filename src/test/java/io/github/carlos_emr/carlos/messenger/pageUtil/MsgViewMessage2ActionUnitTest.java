/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Focused GET-rejection coverage for {@link MsgViewMessage2Action}, a conditional mutator in
 * {@code MutatorActionGetRejectionContractUnitTest}: viewing a message is a GET, but
 * {@code linkMsgDemo=true} writes a msgDemoMap row and must arrive as a POST (CSRFGuard does not
 * validate GET). The broader behaviour lives in {@code MsgViewMessage2ActionTest}.
 *
 * @since 2026-10-02
 */
@Tag("unit")
@Tag("messenger")
@Tag("security")
@DisplayName("MsgViewMessage2Action GET rejection for patient linking")
class MsgViewMessage2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private MessagingManager messagingManager;
    private MessengerDemographicManager messengerDemographicManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        messagingManager = createAndRegisterMock(MessagingManager.class);
        messengerDemographicManager = createAndRegisterMock(MessengerDemographicManager.class);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_msg"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);

        request = new MockHttpServletRequest();
        request.setRequestURI("/carlos/messenger/ViewMessage");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a non-POST link request with 405 before any read or write")
    void shouldReturn405_whenLinkRequestedWithoutPost(String method) throws Exception {
        request.setMethod(method);
        request.addParameter("messageID", "100");
        request.addParameter("linkMsgDemo", "true");
        request.addParameter("demographic_no", "42");

        // Constructed after the static mocks: the action captures the request and its managers
        // in field initializers.
        String result = new MsgViewMessage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(messagingManager, never()).getInboxMessage(any(), anyInt());
        verify(messagingManager, never()).setMessageRead(any(), anyLong(), any());
        verify(messengerDemographicManager, never()).getAttachedDemographicNameMap(any(), anyInt());
        verify(messengerDemographicManager, never()).attachDemographicToMessage(any(), anyInt(), anyInt());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should hand the shown message to the page in the request, not the login's session")
    void shouldPublishShownMessageInRequest_whenViewingMessage() throws Exception {
        request.setMethod("GET");
        request.addParameter("messageID", "100");
        io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage shown =
                new io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage();
        shown.setMessageId("100");
        shown.setThesubject("FAKE subject about patient 42");
        shown.setMessageBody("FAKE body");
        when(messagingManager.getInboxMessage(any(), eq(100))).thenReturn(shown);
        when(messengerDemographicManager.getAttachedDemographicNameMap(any(), eq(100)))
                .thenReturn(java.util.Map.of(42, "FAKE-Doe, Pat"));

        assertThat(new MsgViewMessage2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        // Issue #4673: in the session, a second window opening another message at the same time
        // could replace these between the action and the page it forwards to.
        assertThat(request.getAttribute("viewMessageSubject")).isEqualTo("FAKE subject about patient 42");
        assertThat(request.getAttribute("viewMessageMessage")).isEqualTo("FAKE body");
        assertThat(request.getAttribute("viewMessageNo")).isEqualTo("100");
        assertThat(request.getAttribute("attachedDemographics")).isEqualTo(java.util.Map.of(42, "FAKE-Doe, Pat"));
        assertThat(java.util.Collections.list(request.getSession().getAttributeNames()))
                .noneMatch(name -> name.startsWith("viewMessage") || name.equals("attachedDemographics"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should name the listing patient and the view mode for the page, when opened from a patient's message list")
    void shouldPublishListedPatient_whenOpenedFromPatientMessageList() throws Exception {
        request.setMethod("GET");
        request.addParameter("messageID", "100");
        request.addParameter("from", "encounter");
        request.addParameter("demographic_no", "42");
        io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage shown =
                new io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage();
        shown.setMessageId("100");
        when(messagingManager.getInboxMessage(any(), eq(100))).thenReturn(shown);

        assertThat(new MsgViewMessage2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        // ViewMessage.jsp offers Write to Encounter for the linked patient equal to demographic_no.
        assertThat(request.getAttribute("demographic_no")).isEqualTo("42");
        assertThat(request.getAttribute("from")).isEqualTo("encounter");
    }

    @ParameterizedTest(name = "from={0}, demographic_no={1}")
    @CsvSource({"messenger,42", "encounter,12x", "encounter,0", "encounter,''"})
    @DisplayName("should name no listing patient for an inbox view or a patient number that is not one")
    void shouldNotPublishListedPatient_whenNotOpenedForOneValidPatient(String from, String patient) throws Exception {
        request.setMethod("GET");
        request.addParameter("messageID", "100");
        request.addParameter("from", from);
        request.addParameter("demographic_no", patient);
        io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage shown =
                new io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage();
        shown.setMessageId("100");
        when(messagingManager.getInboxMessage(any(), eq(100))).thenReturn(shown);

        assertThat(new MsgViewMessage2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("demographic_no")).isNull();
        assertThat(request.getAttribute("from")).isEqualTo(from);
    }
}
