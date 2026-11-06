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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The eChart Forms module renders one "started" item per form whose link opens
 * {@code formId=latest}. Issue #4678: the item must be dated and titled from the row that
 * link opens, so the action takes its row from
 * {@link EctFormData#getShortcutLatestPatientForm(String, String)}, whose agreement with the
 * shortcut is pinned by {@code EctFormDataShortcutLatestIntegrationTest}.
 */
@Tag("unit")
@Tag("encounter")
@Tag("form")
@DisplayName("eChart Forms module started-form items")
class EctDisplayForm2ActionUnitTest {
    private static final String PATIENT = "1";

    private MockedStatic<SpringUtils> spring;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<EctFormData> formData;
    private MockHttpServletRequest request;
    private EctSessionBean session;
    private final List<EncounterForm> registry = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(security.hasPrivilege(loggedInInfo, "_form", "r", null)).thenReturn(true);
        EncounterFormDao forms = mock(EncounterFormDao.class);
        when(forms.findAll()).thenAnswer(invocation -> new ArrayList<>(registry));

        request = new MockHttpServletRequest();
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", loggedInInfo);
        request.setContextPath("/carlos");
        session = new EctSessionBean();
        session.providerNo = "999998";
        session.demographicNo = PATIENT;
        session.appointmentNo = "11";

        // SpringUtils is stubbed before EctFormData is mocked, so its static registry lookup
        // resolves to this mock rather than a missing application context.
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
        spring.when(() -> SpringUtils.getBean(EncounterFormDao.class)).thenReturn(forms);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        // Default answers, not CALLS_REAL_METHODS: stubbing would otherwise run the real lookup.
        formData = mockStatic(EctFormData.class);
        formData.when(() -> EctFormData.isRemovedCaisiForm(anyString())).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() {
        try {
            if (formData != null) formData.close();
        } finally {
            try {
                if (servlet != null) servlet.close();
            } finally {
                if (spring != null) spring.close();
            }
        }
    }

    @Test
    void shouldKeepPopupHashPositive_whenStringHashIsMinimumInteger() {
        assertThat("polygenelubricants".hashCode()).isEqualTo(Integer.MIN_VALUE);
        assertThat(NavBarDisplayDAO.popupWindowHash("polygenelubricants")).isEqualTo(2147483648L);
    }

    @Test
    @DisplayName("should date, title and link the item from the row the latest shortcut opens")
    void shouldDescribeShortcutRow_whenFormHasBeenStarted() {
        register("Annual", "formAnnual", "../form/formannual.jsp?demographic_no=");
        stubLatest("formAnnual", row(10, "2026-03-09 14:00:00"));

        NavBarDisplayDAO panel = render();

        assertThat(panel.numItems()).isEqualTo(1);
        NavBarDisplayDAO.Item item = panel.getItem(0);
        assertThat(item.getTitle()).isEqualTo("Annual");
        assertThat(item.getDate()).isEqualTo(Timestamp.valueOf("2026-03-09 14:00:00"));
        assertThat(item.getLinkTitle()).isEqualTo("Annual 09-03-2026 14:00:00");
        assertThat(item.getURL())
                .contains("/carlos/form/forwardshortcutname?formname=Annual&demographic_no=1&appointmentNo=11&formId=latest")
                .endsWith("return false;");
        assertThat(panel.getAutoCompleteItems())
                .anySatisfy(entry -> assertThat(entry.key()).isEqualTo("Annual(09-03-2026 14:00:00)"));
        // The creation-ordered list, which can lead with a different row, is no longer consulted.
        formData.verify(() -> EctFormData.getPatientFormsFromLocalAndRemote(Mockito.any(), anyString(), anyString()), never());
        formData.verify(() -> EctFormData.getShortcutLatestPatientForm(PATIENT, "formAnnual"));
    }

    @Test
    @DisplayName("should list only the new-form menu entry when the patient has not started the form")
    void shouldOfferOnlyNewForm_whenPatientHasNoRows() {
        register("Annual", "formAnnual", "../form/formannual.jsp?demographic_no=");
        stubLatest("formAnnual", null);

        NavBarDisplayDAO panel = render();

        assertThat(panel.numItems()).isZero();
        assertThat(panel.numPopUpMenuItems()).isEqualTo(1);
        assertThat(panel.getPopUpConfig(0).url())
                .startsWith("/carlos/form/formannual?demographic_no=1&formId=0&provNo=999998");
        assertThat(panel.getPopUpText(0)).isEqualTo("Annual");
    }

    @Test
    @DisplayName("should keep the module working and leave the date blank when the chosen row has no edit time")
    void shouldRenderUndatedItem_whenChosenRowHasNoEditTime() {
        register("Annual", "formAnnual", "../form/formannual.jsp?demographic_no=");
        register("CHF", "formchf", "../form/formchf.jsp?demographic_no=");
        stubLatest("formAnnual", row(10, "2026-03-09 14:00:00"));
        stubLatest("formchf", row(30, null));

        NavBarDisplayDAO panel = render();

        assertThat(panel.numItems()).isEqualTo(2);
        NavBarDisplayDAO.Item undated = itemTitled(panel, "CHF");
        assertThat(undated.getDate()).isNull();
        assertThat(undated.getLinkTitle()).isEqualTo("CHF ");
        assertThat(undated.getURL()).contains("formname=CHF&demographic_no=1&appointmentNo=11&formId=latest");
        // A preceding form's date must not leak onto the undated item.
        assertThat(itemTitled(panel, "Annual").getDate()).isEqualTo(Timestamp.valueOf("2026-03-09 14:00:00"));
    }

    @Test
    @DisplayName("should order started forms from most recently edited")
    void shouldSortItemsByEditTime_whenSeveralFormsAreStarted() {
        register("Annual", "formAnnual", "../form/formannual.jsp?demographic_no=");
        register("CHF", "formchf", "../form/formchf.jsp?demographic_no=");
        stubLatest("formAnnual", row(10, "2026-01-01 08:00:00"));
        stubLatest("formchf", row(30, "2026-05-01 08:00:00"));

        NavBarDisplayDAO panel = render();

        assertThat(panel.getItem(0).getTitle()).isEqualTo("CHF");
        assertThat(panel.getItem(1).getTitle()).isEqualTo("Annual");
    }

    private NavBarDisplayDAO render() {
        EctDisplayForm2Action action = spy(new EctDisplayForm2Action());
        doReturn("Forms").when(action).getText(anyString());
        NavBarDisplayDAO panel = new NavBarDisplayDAO();
        assertThat(action.getInfo(session, request, panel)).isTrue();
        return panel;
    }

    @Test
    void shouldSerializeGuardArgumentsAsJson_whenFormNameContainsPunctuation() throws Exception {
        String name = "Caregiver's, \\\"record\\\" \\\\ suivi";
        register(name, "formCaregiver", "../form/formcaregiver.jsp?demographic_no=");
        String expression = render().getAutoCompleteItems().getFirst().jsExpression();
        String prefix = "openEncounterForm.apply(null,";
        assertThat(expression).startsWith(prefix).endsWith(");");
        var arguments = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                expression.substring(prefix.length(), expression.length() - 2));
        assertThat(arguments.size()).isEqualTo(3);
        assertThat(arguments.get(0).asText()).contains("demographic_no=1&formId=0");
        assertThat(arguments.get(1).asText()).endsWith("new");
        assertThat(arguments.get(2).asText()).isEqualTo(name);
    }

    private void register(String name, String table, String value) {
        EncounterForm form = new EncounterForm();
        form.setFormName(name);
        form.setFormTable(table);
        form.setFormValue(value);
        form.setDisplayOrder(registry.size() + 1);
        registry.add(form);
    }

    private void stubLatest(String table, EctFormData.PatientForm row) {
        formData.when(() -> EctFormData.getShortcutLatestPatientForm(PATIENT, table)).thenReturn(row);
    }

    private static EctFormData.PatientForm row(int id, String edited) {
        return new EctFormData.PatientForm(null, id, Integer.valueOf(PATIENT),
                java.sql.Date.valueOf("2026-01-01"), edited == null ? null : Timestamp.valueOf(edited));
    }

    private static NavBarDisplayDAO.Item itemTitled(NavBarDisplayDAO panel, String title) {
        for (int index = 0; index < panel.numItems(); index++) {
            if (title.equals(panel.getItem(index).getTitle())) {
                return panel.getItem(index);
            }
        }
        throw new AssertionError("No item titled " + title);
    }
}
