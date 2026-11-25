// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.QueryPage;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.Test;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class BillingreferralEdit2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"123", "000123", "+123"})
    void shouldRetainNumericReferralSearch_whenAdvancedInputContainsLeadingZerosOrSign(String input) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage("123", null, null, null, null, true, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest(); request.addParameter("nameQuery", input);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new BillingreferralEdit2Action().advancedSearch()).isEqualTo("list");
        }
        verify(dao).findDirectoryPage("123", null, null, null, null, true, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "Synthetic"})
    void shouldRetainNameSearch_whenAdvancedInputIsNotAnInteger(String input) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage(null, input, "", null, null, false, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest(); request.addParameter("nameQuery", input);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new BillingreferralEdit2Action().advancedSearch()).isEqualTo("list");
        }
        verify(dao).findDirectoryPage(null, input, "", null, null, false, 0);
    }

    /** #6336: the invalid tablePage answers 400 and ends processing before the directory is read. */
    @ParameterizedTest
    @ValueSource(strings = {"list", "searchByNo", "searchBySpecialty", "searchByName", "advancedSearch"})
    void shouldAnswer400WithoutQuerying_whenTablePageIsInvalid(String method) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        var request = new MockHttpServletRequest();
        request.addParameter("tablePage", "-1");
        var response = new MockHttpServletResponse();

        String result = run(request, response, method);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(dao);
    }

    @Test
    void shouldReadDirectoryByReferralNumber_whenSearchByNoRoute() {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        var page = new QueryPage<ProfessionalSpecialist>(List.of(), 0, 2, 15);
        when(dao.findDirectoryPage("00123", null, null, null, null, true, 2)).thenReturn(page);
        var request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.addParameter("tablePage", "2");
        request.addParameter("method", "searchByNo");
        request.addParameter("search", "00123");

        assertThat(run(request, new MockHttpServletResponse(), "searchByNo", "00123")).isEqualTo("list");

        verify(dao).findDirectoryPage("00123", null, null, null, null, true, 2);
        assertThat(request.getAttribute("searchBy")).isEqualTo("searchByNo");
        assertThat(request.getAttribute("serverTablePage")).isSameAs(page);
        assertThat(request.getAttribute("serverTableAction")).isEqualTo("/carlos/admin/ManageBillingReferral");
        assertThat(request.getAttribute("serverTableMethod")).isEqualTo("get");
        @SuppressWarnings("unchecked")
        var retained = (java.util.Map<String, String[]>) request.getAttribute("serverTableParameters");
        assertThat(retained).containsOnlyKeys("method", "search");
        assertThat(retained.get("search")).containsExactly("00123");
    }

    @Test
    void shouldReadDirectoryBySpecialty_whenSearchBySpecialtyRoute() {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage(null, null, null, "FAKE-Cardiology", null, true, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest();

        assertThat(run(request, new MockHttpServletResponse(), "searchBySpecialty", "FAKE-Cardiology"))
                .isEqualTo("list");

        verify(dao).findDirectoryPage(null, null, null, "FAKE-Cardiology", null, true, 0);
        assertThat(request.getAttribute("searchBy")).isEqualTo("searchBySpecialty");
    }

    @Test
    void shouldKeepSelectedReferralIds_whenTheDirectoryPageIsPublished() {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage(null, null, null, null, null, true, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var picked = new ProfessionalSpecialist(42);
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute("billingReferralAdminCheckList", new java.util.ArrayList<>(List.of(picked)));

        assertThat(run(request, new MockHttpServletResponse(), "list")).isEqualTo("list");

        assertThat(request.getAttribute("selectedReferralIds")).isEqualTo(java.util.Set.of(42));
        assertThat(request.getAttribute("searchBy")).isEqualTo("searchByName");
    }

    private String run(MockHttpServletRequest request, MockHttpServletResponse response, String method, String... search) {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new BillingreferralEdit2Action();
            if (search.length > 0) action.setSearch(search[0]);
            return switch (method) {
                case "searchByNo" -> action.searchByNo();
                case "searchBySpecialty" -> action.searchBySpecialty();
                case "searchByName" -> action.searchByName();
                case "advancedSearch" -> action.advancedSearch();
                default -> action.list();
            };
        }
    }
}
