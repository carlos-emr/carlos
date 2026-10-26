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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opening the BC service/diagnostic association editor (issue #4439 D14). Every request opened it in edit mode, which
 * makes the service code read-only and skips the save's duplicate check, and an empty code failed validation into a
 * result nothing maps; so the list's Create New Association link pointed back at the list. Without a code the editor
 * now opens empty, in create mode.
 *
 * @since 2026-10-22
 */
@DisplayName("BC service/diagnostic association editor: create and edit modes")
@Tag("unit")
@Tag("billing")
class EditServiceCodeAssoc2ActionUnitTest extends CarlosUnitTestBase {

    private static final String CODE = "00100";
    private static final String DX = "250";

    private MockHttpServletRequest request;
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;
    private MockedConstruction<BillingAssociationPersistence> persistence;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/billing/CA/BC/editServiceCodeAssocAction");
        LoggedInInfo info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_billing", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        persistence = mockConstruction(BillingAssociationPersistence.class, (mock, context) -> {
            ServiceCodeAssociation existing = new ServiceCodeAssociation();
            existing.setServiceCode(CODE);
            existing.addDXCode(DX);
            when(mock.getServiceCodeAssocByCode(CODE)).thenReturn(existing);
            // The lookup names the code only when it has diagnoses.
            when(mock.getServiceCodeAssocByCode("99999")).thenReturn(new ServiceCodeAssociation());
        });
    }

    @AfterEach
    void tearDown() {
        persistence.close();
        servlet.close();
    }

    private EditServiceCodeAssoc2Action open(String svcCode) {
        EditServiceCodeAssoc2Action action = new EditServiceCodeAssoc2Action();
        action.setSvcCode(svcCode);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        return action;
    }

    private ServiceCodeAssociation shown() {
        return (ServiceCodeAssociation) request.getAttribute("serviceCodeAssociation");
    }

    /** Create New Association names no code. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void shouldOpenAnEmptyEditorInCreateMode_whenNoServiceCodeIsNamed(String svcCode) {
        EditServiceCodeAssoc2Action action = open(svcCode);

        assertThat(request.getAttribute("mode")).isEqualTo("");
        assertThat(action.getMode()).isEqualTo("");
        assertThat(shown().getServiceCode()).isNull();
        assertThat(shown().getDxCodes()).isEmpty();
        assertThat(persistence.constructed()).isEmpty();
    }

    @Test
    void shouldOpenTheAssociationToEdit_whenItsServiceCodeIsNamed() {
        open(CODE);

        assertThat(request.getAttribute("mode")).isEqualTo("edit");
        assertThat(shown().getServiceCode()).isEqualTo(CODE);
        assertThat(shown().getDxCodes()).isEqualTo(List.of(DX));
        verify(persistence.constructed().get(0)).getServiceCodeAssocByCode(CODE);
    }

    @Test
    void shouldReadTheServiceCode_fromTheRequestWhenStrutsDidNotBindIt() {
        request.addParameter("svcCode", CODE);

        open(null);

        assertThat(request.getAttribute("mode")).isEqualTo("edit");
        assertThat(shown().getServiceCode()).isEqualTo(CODE);
    }

    /** A code with no association (removed meanwhile) is a new one: its box must stay editable. */
    @Test
    void shouldOpenCreateMode_whenTheNamedCodeHasNoAssociation() {
        open("99999");

        assertThat(request.getAttribute("mode")).isEqualTo("");
        assertThat(shown().getServiceCode()).isNull();
    }

    @Test
    void shouldRefuse_withoutBillingWrite() {
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), nullable(String.class))).thenReturn(false);
        EditServiceCodeAssoc2Action action = new EditServiceCodeAssoc2Action();
        action.setSvcCode(CODE);

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        assertThat(persistence.constructed()).isEmpty();
    }

    /** The list's link opens the editor with no code; it pointed at the list itself. */
    @Test
    void shouldOpenTheEditor_fromCreateNewAssociation() throws IOException {
        String list = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/manageSVCDXAssoc.jsp"),
                StandardCharsets.UTF_8);
        assertThat(list)
                .contains("<a href=\"<%= request.getContextPath() %>/billing/CA/BC/editServiceCodeAssocAction\">Create")
                .doesNotContain("/billing/CA/BC/showServiceCodeAssocs\">Create");
    }
}
