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
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.WebUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class BillingreferralEdit2ActionUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPublishIndependentSnapshots_whenTwoTabsSelectRemoveAndClearLabels() throws Exception {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        for (int id : new int[]{7, 8}) {
            var entry = new ProfessionalSpecialist();
            ReflectionTestUtils.setField(entry, "id", id);
            entry.setLastName("Specialist" + id);
            when(dao.find(id)).thenReturn(entry);
        }
        var session = new MockHttpSession() {
            @Override
            public void setAttribute(String name, Object value) {
                if ("billingReferralAdminCheckList".equals(name)) {
                    assertThat(Thread.holdsLock(WebUtils.getSessionMutex(this))).isTrue();
                }
                super.setAttribute(name, value);
            }
        };
        assertThat(changeLabels(session, "7", true)).containsExactly(7);
        var firstView = (List<?>) session.getAttribute("billingReferralAdminCheckList");
        assertThat(changeLabels(session, "8", true)).containsExactly(7, 8);
        assertThat(firstView).hasSize(1);
        assertThat(changeLabels(session, "8", true)).containsExactly(7, 8);
        assertThat(changeLabels(session, "7", false)).containsExactly(8);
        assertThat(changeLabels(session, null, false)).isEmpty();
        assertThat(changeLabels(session, null, false)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "invalid", "0"})
    void shouldLeaveSelectionUntouched_whenReferralIsMissingOrInvalid(String id) throws Exception {
        createAndRegisterMock(ProfessionalSpecialistDao.class);
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var previous = List.of(new ProfessionalSpecialist());
        request.getSession().setAttribute("billingReferralAdminCheckList", previous);
        request.setParameter("id", id);
        request.setParameter("checked", "true");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new BillingreferralEdit2Action().modifyBatch();
        }
        assertThat(response.getStatus()).isEqualTo("7".equals(id) ? 404 : 400);
        assertThat(request.getSession().getAttribute("billingReferralAdminCheckList")).isSameAs(previous);
    }

    private List<Integer> changeLabels(MockHttpSession session, String id, boolean checked) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setSession(session);
        if (id == null) {
            request.setParameter("clear", "true");
        } else {
            request.setParameter("id", id);
            request.setParameter("checked", Boolean.toString(checked));
        }
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new BillingreferralEdit2Action().modifyBatch();
        }
        assertThat(response.getStatus()).isEqualTo(200);
        var result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(result.isArray()).isTrue();
        return result.valueStream().map(row -> row.get("id").asInt()).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "000123", "+123"})
    void shouldRetainNumericReferralSearch_whenAdvancedInputContainsLeadingZerosOrSign(String input) {
        var dao = createAndRegisterMock(ProfessionalSpecialistDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        when(dao.findDirectoryPage("123", null, null, null, null, true, 0))
                .thenReturn(new QueryPage<>(List.of(), 0, 0, 15));
        var request = new MockHttpServletRequest();
        request.addParameter("nameQuery", input);
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
        var request = new MockHttpServletRequest();
        request.addParameter("nameQuery", input);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new BillingreferralEdit2Action().advancedSearch()).isEqualTo("list");
        }
        verify(dao).findDirectoryPage(null, input, "", null, null, false, 0);
    }
}
