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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BC billing code edit ({@code billing/CA/BC/billingEditCode}): every form and request on its page posts,
 * and each of its branches runs only after the session and billing write checks.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC billing code edit accepts only POST and checks rights before any branch")
class BillingEditCode2ActionUnitTest extends CarlosUnitTestBase {

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final BillingServiceDao billingServices = mock(BillingServiceDao.class);
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        registerMock(BillingServiceDao.class, billingServices);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(mock(LoggedInInfo.class));
        request.getSession().setAttribute("user", "999998");
    }

    @AfterEach
    void tearDown() {
        loggedIn.close();
        servlet.close();
    }

    private void grantBillingWrite(boolean granted) {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull())).thenReturn(granted);
    }

    private void codeUpdate() {
        request.setParameter("method", "ajaxCodeUpdate");
        request.setParameter("codeId", "12");
        request.setParameter("id", "1");
        request.setParameter("val", "99.99");
        request.setParameter("billService", "2026-01-01");
        request.setParameter("termDate", "2026-12-31");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should answer 405 and change nothing for a method other than POST")
    void shouldChangeNothing_whenNotPost(String method) throws Exception {
        request.setMethod(method);
        grantBillingWrite(true);
        codeUpdate();

        assertThat(new BillingEditCode2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(billingServices);
    }

    @Test
    @DisplayName("should refuse a code update without billing write, before touching the fee schedule")
    void shouldRefuseCodeUpdate_withoutBillingWrite() {
        request.setMethod("POST");
        grantBillingWrite(false);
        codeUpdate();

        assertThatThrownBy(() -> new BillingEditCode2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(billingServices);
    }

    @Test
    @DisplayName("should save a code update with billing write on POST")
    void shouldSaveCodeUpdate_withBillingWrite() throws Exception {
        request.setMethod("POST");
        grantBillingWrite(true);
        codeUpdate();
        BillingService code = new BillingService();
        code.setBillingserviceNo(12);
        when(billingServices.find(12)).thenReturn(code);

        assertThat(new BillingEditCode2Action().execute()).isEqualTo(ActionSupport.NONE);

        verify(billingServices).merge(code);
        assertThat(code.getValue()).isEqualTo("99.99");
    }

    @Test
    @DisplayName("should answer the fields the page reads from the saved code row")
    void shouldAnswerFieldsThePageReads_fromTheSavedCodeRow() throws Exception {
        request.setMethod("POST");
        grantBillingWrite(true);
        codeUpdate();
        BillingService code = new BillingService();
        code.setBillingserviceNo(12);
        when(billingServices.find(12)).thenReturn(code);

        new BillingEditCode2Action().execute();

        // billingEditCode.jsp reads id, value, billingserviceDate and terminationDate. The row has
        // no percentage-limit property any more (issue #5307), so serializing it touches no
        // association and cannot fail on the detached row.
        com.fasterxml.jackson.databind.JsonNode answer =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(answer.get("id").asText()).isEqualTo("1");
        assertThat(answer.get("value").asText()).isEqualTo("99.99");
        assertThat(answer.has("billingserviceDate")).isTrue();
        assertThat(answer.has("terminationDate")).isTrue();
        assertThat(answer.has("billingPercLimit")).isFalse();
    }

    @Test
    @DisplayName("should send a logged-out request to the logout result before any branch")
    void shouldLogOut_whenNoSessionUser() throws Exception {
        request.setMethod("POST");
        request.getSession().removeAttribute("user");
        codeUpdate();

        assertThat(new BillingEditCode2Action().execute()).isEqualTo("Logout");
        verifyNoInteractions(billingServices);
    }

    @Test
    @DisplayName("should return to the code list without an edit when no button was pressed")
    void shouldReturnToList_whenNoSubmitButton() throws Exception {
        request.setMethod("POST");
        grantBillingWrite(true);

        assertThat(new BillingEditCode2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(billingServices);
    }
}
