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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * {@link WCBAction22Action}, the BC WCB form's save ({@code billing/CA/BC/formwcb}): it writes a
 * {@code wcb} row, so it takes only the form's POST (issue #4864 item 8). A GET or HEAD used to save
 * a form as well.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("WCBAction22Action")
class WCBAction22ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private BillingmasterDAO billingmasterDao;

    @BeforeEach
    void wire() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);

        // The save also finds the DAO through the web context, where it holds two beans of the type
        // until #4864 Part 1 resolves it through SpringUtils; either way this DAO answers.
        MockServletContext context = new MockServletContext();
        WebApplicationContext web = mock(WebApplicationContext.class);
        lenient().when(web.getBean(BillingmasterDAO.class)).thenReturn(billingmasterDao);
        context.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, web);

        request = new MockHttpServletRequest(context);
        request.setContextPath("/carlos");
        MockHttpSession session = new MockHttpSession(context);
        session.setAttribute("user", "97001");
        LoggedInInfo.setLoggedInInfoIntoSession(session, mock(LoggedInInfo.class));
        request.setSession(session);
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void close() {
        servlet.close();
    }

    private WCBAction22Action postedForm() {
        WCBAction22Action action = new WCBAction22Action();
        action.setDemographic_no("42");
        action.setW_duration("1");
        action.setW_lname("FAKE-PW-saved");
        return action;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a save that is not a POST, and write nothing")
    void shouldRefuseWithoutSaving_forMethodOtherThanPost(String method) throws Exception {
        request.setMethod(method);

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(billingmasterDao);
    }

    @Test
    @DisplayName("should save the form's POST as a new wcb row")
    void shouldSaveTheForm_forPost() throws Exception {
        request.setMethod("POST");

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<WCB> saved = ArgumentCaptor.forClass(WCB.class);
        verify(billingmasterDao).save(saved.capture());
        assertThat(saved.getValue().getDemographic_no()).isEqualTo(42);
        assertThat(saved.getValue().getW_lname()).isEqualTo("FAKE-PW-saved");
        assertThat(saved.getValue().getProvider_no()).isEqualTo("97001");
        assertThat(response.getStatus()).isNotEqualTo(405);
    }

    @ParameterizedTest(name = "wcbFormId={0}")
    @ValueSource(strings = {"7", " 12 ", "0.5"})
    @DisplayName("should refuse a save that names a saved form, and write nothing")
    void shouldRefuseWithoutSaving_forSavedFormId(String formId) throws Exception {
        request.setMethod("POST");
        WCBAction22Action action = postedForm();
        action.setWcbFormId(formId);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(billingmasterDao);
    }

    @ParameterizedTest(name = "wcbFormId={0}")
    @ValueSource(strings = {"0", " 0 ", ""})
    @DisplayName("should save a new form, which posts no saved form id")
    void shouldSaveTheForm_forNewFormId(String formId) throws Exception {
        request.setMethod("POST");
        WCBAction22Action action = postedForm();
        action.setWcbFormId(formId);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        verify(billingmasterDao).save(any(WCB.class));
        assertThat(response.getStatus()).isNotEqualTo(409);
    }

    @Test
    @DisplayName("should send a plain Save to the saved form's own view")
    void shouldRedirectToSavedForm_forPlainSave() throws Exception {
        request.setMethod("POST");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<WCB>getArgument(0).setId(88);
            return null;
        }).when(billingmasterDao).save(any(WCB.class));

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/billing/CA/BC/viewformwcb?demographic_no=42&formId=88&provNo=97001");
    }
}
