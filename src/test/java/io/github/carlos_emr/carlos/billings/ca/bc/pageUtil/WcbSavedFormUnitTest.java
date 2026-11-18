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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * A saved BC WCB form opens for viewing only (issue #4947). {@link WCBAction22Action#save()} always inserts a new
 * {@code wcb} row, so the page offered Save on a saved form and saving it stored a second copy; and a plain Save
 * redirected to the form page without naming the form it saved, which then failed.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("A saved WCB form")
class WcbSavedFormUnitTest extends CarlosUnitTestBase {

    private static final Path FORM_WCB_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/formwcb.jsp");

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private BillingmasterDAO billingmasterDao;

    @BeforeEach
    void wire() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        request = new MockHttpServletRequest("POST", "/carlos/billing/CA/BC/formwcb");
        request.setContextPath("/carlos");
        MockHttpSession session = new MockHttpSession();
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
        return action;
    }

    @Test
    @DisplayName("should refuse a save that names a saved form, and write nothing")
    void shouldRefuseWithoutSaving_whenThePostNamesASavedForm() throws Exception {
        request.setParameter("wcbFormId", "7");
        request.setParameter("saveAndClose", "Save and Close");

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("This WCB form is already saved.");
        verifyNoInteractions(billingmasterDao);
    }

    @ParameterizedTest(name = "wcbFormId={0}")
    @NullSource
    @ValueSource(strings = {"0", ""})
    @DisplayName("should save a new form, which posts no saved form's id")
    void shouldSaveTheForm_whenThePostNamesNoSavedForm(String wcbFormId) throws Exception {
        if (wcbFormId != null) {
            request.setParameter("wcbFormId", wcbFormId);
        }
        request.setParameter("saveAndClose", "Save and Close");

        assertThat(postedForm().execute()).isEqualTo("saveAndClose");

        verify(billingmasterDao).save(any(WCB.class));
    }

    @Test
    @DisplayName("should show the form just saved after a plain Save")
    void shouldShowTheSavedForm_afterAPlainSave() throws Exception {
        doAnswer(invocation -> {
            ((WCB) invocation.getArgument(0)).setId(55);
            return null;
        }).when(billingmasterDao).save(any(WCB.class));

        assertThat(postedForm().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/billing/CA/BC/viewformwcb?demographic_no=42&formId=55");
    }

    @Test
    @DisplayName("should offer no save on a saved form, and post the form's id back")
    void shouldOfferNoSave_onASavedForm() throws IOException {
        String jsp = Files.readString(FORM_WCB_JSP, StandardCharsets.UTF_8);
        int guard = jsp.indexOf("<%if (!readonly) {%>");
        int end = jsp.indexOf("<input type=\"button\" value=\"Cancel\"", guard);

        assertThat(guard).as("the save buttons' read-only guard").isPositive();
        String guarded = jsp.substring(guard, end);
        assertThat(guarded).contains("name=\"save\"", "name=\"saveAndClose\"", "name=\"saveandbill\"");
        assertThat(jsp.substring(0, guard)).doesNotContain("type=\"submit\"");
        assertThat(jsp.substring(end)).doesNotContain("type=\"submit\"");
        assertThat(jsp).contains("<input type=\"hidden\" name=\"wcbFormId\" id=\"wcbFormId\" "
                + "value=\"<%= SafeEncode.forHtmlAttribute(bound.getWcbFormId()) %>\"/>");
    }
}
