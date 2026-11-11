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

import java.util.List;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saving a BC service/diagnostic code association: the save ignored its own checks, so a diagnosis was stored with no
 * service code, a second association for a code already associated, or codes that do not exist (issue #4941; #4439
 * D13). It now shows the editor again, with the posted codes and the reasons, and saves nothing.
 *
 * @since 2026-10-18
 */
@DisplayName("BC service/diagnostic association save")
@Tag("unit")
@Tag("billing")
class SaveAssoc2ActionUnitTest extends CarlosUnitTestBase {

    private static final String CODE = "00100";
    private static final String DX = "250";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedConstruction<BillingAssociationPersistence> persistence;
    private SaveAssoc2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/billing/CA/BC/saveAssocAction");
        LoggedInInfo info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_billing", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        response = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        persistence = mockConstruction(BillingAssociationPersistence.class, (mock, context) -> {
            when(mock.serviceCodeExists(CODE)).thenReturn(true);
            when(mock.dxcodeExists(DX)).thenReturn(true);
            when(mock.assocExists(CODE)).thenReturn(true);
        });
        // The bundle needs a Struts container; the key and the code stand in for the message.
        action = new SaveAssoc2Action() {
            @Override
            public String getText(String key, String[] args) {
                return key + ":" + String.join(",", args);
            }
        };
        action.setXml_diagnostic_detail2("");
        action.setXml_diagnostic_detail3("");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
        servlet.close();
    }

    private BillingAssociationPersistence persistence() {
        return persistence.constructed().get(0);
    }

    @Test
    void shouldReplaceTheAssociation_whenAnEditIsValid() {
        action.setMode("edit");
        action.setXml_other1(CODE);
        action.setXml_diagnostic_detail1(DX);

        assertThat(action.execute()).isEqualTo("success");

        verify(persistence()).saveServiceCodeAssociation(argThat(a -> CODE.equals(a.getServiceCode())
                && a.getDxCodes().equals(List.of(DX))), eq("edit"));
    }

    /** "" is what the editor posted when it opened empty; a blank code is no code either. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRefuseAndShowTheEditorAgain_whenTheServiceCodeIsMissing(String serviceCode) {
        action.setMode("");
        action.setXml_other1(serviceCode);
        action.setXml_diagnostic_detail1(DX);

        assertThat(action.execute()).isEqualTo("input");

        verify(persistence(), never()).saveServiceCodeAssociation(any(), anyString());
        assertThat(action.getActionErrors()).containsExactly("oscar.billing.CA.BC.billingBC.error.invalidsvccode:");
        assertThat(request.getAttribute("actionErrors")).isEqualTo(List.copyOf(action.getActionErrors()));
        ServiceCodeAssociation shown = (ServiceCodeAssociation) request.getAttribute("serviceCodeAssociation");
        assertThat(shown.getDxCodes()).containsExactly(DX);
        assertThat(request.getAttribute("mode")).isEqualTo("");
    }

    @Test
    void shouldRefuse_whenANewAssociationNamesACodeAlreadyAssociated() {
        action.setMode("");
        action.setXml_other1(CODE);
        action.setXml_diagnostic_detail1(DX);

        assertThat(action.execute()).isEqualTo("input");

        verify(persistence(), never()).saveServiceCodeAssociation(any(), anyString());
        assertThat(action.getActionErrors()).containsExactly("oscar.billing.CA.BC.billingBC.error.assocexists:" + CODE);
        assertThat(((ServiceCodeAssociation) request.getAttribute("serviceCodeAssociation")).getServiceCode()).isEqualTo(CODE);
    }

    @Test
    void shouldRefuse_whenTheServiceCodeOrADiagnosisDoesNotExist() {
        action.setMode("edit");
        action.setXml_other1("99999");
        action.setXml_diagnostic_detail1("Z0000");

        assertThat(action.execute()).isEqualTo("input");

        verify(persistence(), never()).saveServiceCodeAssociation(any(), anyString());
        assertThat(action.getActionErrors()).containsExactly(
                "oscar.billing.CA.BC.billingBC.error.invalidsvccode:99999", "oscar.billing.CA.BC.billingBC.error.invaliddxcode:Z0000");
        assertThat(request.getAttribute("mode")).isEqualTo("edit");
    }

    /** The editor posts; a GET or HEAD is refused before any code is checked or saved (#4439 D12). */
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseWith405_whenTheSaveIsNotPosted(String httpMethod) {
        request.setMethod(httpMethod);
        action.setMode("edit");
        action.setXml_other1(CODE);
        action.setXml_diagnostic_detail1(DX);

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(persistence.constructed()).isEmpty();
    }

    @Test
    void shouldPutTheCodeIntoTheMessageAsText_forACodeWithMarkup() {
        action.setMode("");
        action.setXml_other1("<b>1</b>");
        action.setXml_diagnostic_detail1(DX);

        action.execute();

        // The messages are HTML (each ends in a line break), so a typed code goes in as text.
        assertThat(action.getActionErrors()).containsExactly("oscar.billing.CA.BC.billingBC.error.invalidsvccode:&lt;b&gt;1&lt;/b&gt;");
    }
}
