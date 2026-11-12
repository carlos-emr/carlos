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
package io.github.carlos_emr.carlos.form.pageUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Administration ▸ Select Forms with forms that share a display order, as the BC seed has them: the page listed one
 * form per order, and Hide, Up and Down acted on every form sharing an order (issue #4945).
 *
 * @since 2026-10-18
 */
@DisplayName("Select Forms with shared display orders")
@Tag("unit")
@Tag("form")
class SelectFormsSharedOrderUnitTest extends CarlosUnitTestBase {

    private final List<EncounterForm> forms = new ArrayList<>();
    private MockHttpServletRequest request;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        // The BC seed's shown forms, plus one hidden form and a removed CAISI form someone left shown.
        Map<String, Integer> seed = new LinkedHashMap<>();
        seed.put("BC-AR", 1);
        seed.put("BC-AR 2007", 1);
        seed.put("BC-LabourBirth", 1);
        seed.put("BC-NewBorn", 1);
        seed.put("BC Birth Sum 2008", 2);
        seed.put("BC-NewBorn 2008", 2);
        seed.put("Rourke2017", 38);
        seed.put("BC-AR 2020", 0);
        seed.put("Discharge Summary", 5);
        seed.forEach((name, order) -> {
            EncounterForm form = new EncounterForm();
            form.setFormName(name);
            form.setDisplayOrder(order);
            forms.add(form);
        });
        EncounterFormDao dao = createAndRegisterMock(EncounterFormDao.class);
        when(dao.findAll()).thenAnswer(invocation -> new ArrayList<>(forms));
        when(dao.findByFormName(anyString())).thenAnswer(invocation ->
                forms.stream().filter(f -> f.getFormName().equals(invocation.getArgument(0))).toList());

        request = new MockHttpServletRequest("POST", "/form/select");
        LoggedInInfo info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    private int order(String name) {
        return forms.stream().filter(f -> f.getFormName().equals(name)).findFirst().orElseThrow().getDisplayOrder();
    }

    private List<String> shownOnThePage() throws Exception {
        new FrmSetupSelect2Action().execute();
        @SuppressWarnings("unchecked")
        Collection<EncounterForm> shown = (Collection<EncounterForm>) request.getAttribute("formShownVector");
        return shown.stream().map(EncounterForm::getFormName).toList();
    }

    private void press(String button, String formName) throws Exception {
        FrmSelect2Action select = new FrmSelect2Action();
        select.setForward(button);
        select.setSelectedDeleteTypes(new String[]{formName});
        select.execute();
    }

    private List<Integer> shownOrders() {
        return forms.stream().filter(f -> !f.isHidden() && !"Discharge Summary".equals(f.getFormName()))
                .map(EncounterForm::getDisplayOrder).sorted().toList();
    }

    @Test
    void shouldListEveryShownForm_byOrderThenName() throws Exception {
        assertThat(shownOnThePage()).containsExactly("BC-AR", "BC-AR 2007", "BC-LabourBirth", "BC-NewBorn",
                "BC Birth Sum 2008", "BC-NewBorn 2008", "Rourke2017");
    }

    @Test
    void shouldHideOnlyTheChosenForm_whenItSharesItsOrder() throws Exception {
        press("delete", "BC-AR");

        assertThat(order("BC-AR")).isZero();
        assertThat(shownOnThePage()).containsExactly("BC-AR 2007", "BC-LabourBirth", "BC-NewBorn",
                "BC Birth Sum 2008", "BC-NewBorn 2008", "Rourke2017");
        assertThat(shownOrders()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void shouldMoveOneFormUpAndDown_whenItSharedItsOrder() throws Exception {
        press("up", "BC-AR 2007");
        assertThat(shownOnThePage()).startsWith("BC-AR 2007", "BC-AR", "BC-LabourBirth");
        assertThat(shownOrders()).doesNotHaveDuplicates();

        press("down", "BC-AR 2007");
        press("down", "BC-AR 2007");
        assertThat(shownOnThePage()).startsWith("BC-AR", "BC-LabourBirth", "BC-AR 2007", "BC-NewBorn");
        assertThat(shownOrders()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void shouldHideARemovedCaisiForm_whenRenumbering() throws Exception {
        press("up", "BC-NewBorn");

        assertThat(order("Discharge Summary")).isZero();
    }
}
