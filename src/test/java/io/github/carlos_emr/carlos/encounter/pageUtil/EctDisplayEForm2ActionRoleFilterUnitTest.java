/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The eChart eForms panel lists a patient's stored eForms. A copy whose eForm carries a role type
 * ({@code _eform.<roleType>}) is listed only when the login's role holds that right, as the patient's eForm lists
 * in {@code EFormUtil} already do.
 *
 * @since 2026-10-20
 */
@Tag("unit")
@Tag("encounter")
@DisplayName("eChart eForms panel role filter")
class EctDisplayEForm2ActionRoleFilterUnitTest extends CarlosUnitTestBase {
    private static final String ROLE_NAME = "doctor,999998";

    private MockHttpServletRequest request;
    private EctSessionBean bean;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void prepare() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        var user = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), isNull())).thenReturn(true);
        request = new MockHttpServletRequest();
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        bean = mock(EctSessionBean.class);
        bean.demographicNo = "101";
        bean.appointmentNo = "0";
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
    }

    @AfterEach
    void closeServletContext() {
        servlet.close();
    }

    private static EFormData storedForm(int id, String name, String roleType) {
        var form = new EFormData();
        form.setId(id);
        form.setFormName(name);
        form.setSubject("FAKE subject");
        form.setFormDate(new Date(1000));
        form.setRoleType(roleType);
        return form;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should list a role-restricted copy only when the login's role holds its right")
    void shouldListRestrictedCopy_onlyWithTheRoleRight(boolean granted) {
        var action = spy(new EctDisplayEForm2Action());
        doReturn("eForms").when(action).getText(anyString());
        var open = storedForm(201, "FAKE open form", null);
        var restricted = storedForm(202, "FAKE restricted form", "FAKE_ROLE");
        var rights = new Vector<Object>(List.of(new Properties(), new Vector<String>()));
        try (var forms = mockStatic(EFormUtil.class); var privileges = mockStatic(OscarRoleObjectPrivilege.class)) {
            forms.when(() -> EFormUtil.listEForms(EFormUtil.DATE, EFormUtil.CURRENT, ROLE_NAME)).thenReturn(new ArrayList<>());
            forms.when(() -> EFormUtil.listPatientEformsCurrent(101, true, 0, 100)).thenReturn(List.of(open, restricted));
            privileges.when(() -> OscarRoleObjectPrivilege.getPrivilegeProp("_eform.FAKE_ROLE")).thenReturn(rights);
            privileges.when(() -> OscarRoleObjectPrivilege.checkPrivilege(eq(ROLE_NAME), any(Properties.class), anyList()))
                    .thenReturn(granted);
            var panel = new NavBarDisplayDAO();

            assertThat(action.getInfo(bean, request, panel)).isTrue();

            List<String> listed = new ArrayList<>();
            for (int i = 0; i < panel.numItems(); i++) {
                listed.add(panel.getItem(i).getURL());
            }
            assertThat(listed).anyMatch(url -> url.contains("fdid=201"));
            if (granted) {
                assertThat(listed).hasSize(2).anyMatch(url -> url.contains("fdid=202"));
            } else {
                assertThat(listed).hasSize(1).noneMatch(url -> url.contains("fdid=202"));
            }
        }
    }
}
