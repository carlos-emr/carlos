// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.bc.data.SupServiceCodeAssocDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupServiceCodeAssoc2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SupServiceCodeAssocDAO dao;
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;
    private MockedConstruction<BillingAssociationPersistence> persistence;
    private SupServiceCodeAssoc2Action action;
    private LoggedInInfo info;
    @BeforeEach void setUp() {
        request = new MockHttpServletRequest("POST", "/billing/CA/BC/supServiceCodeAssocAction");
        request.setContextPath("/carlos"); response = new MockHttpServletResponse();
        info = mock(LoggedInInfo.class); LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_billing", "w", null)).thenReturn(true);
        when(security.hasPrivilege(info, "_admin.billing,_admin", "w", null)).thenReturn(true);
        dao = createAndRegisterMock(SupServiceCodeAssocDAO.class);
        when(dao.getServiceCodeAssociactions()).thenReturn(List.of());
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        persistence = mockConstruction(BillingAssociationPersistence.class, (mock, context) -> {
            when(mock.serviceCodeExists("00100")).thenReturn(true);
            when(mock.serviceCodeExists("11000")).thenReturn(true);
        });
        action = new SupServiceCodeAssoc2Action() {
            @Override public String getText(String key, String defaultValue) { return key; }
        };
    }
    @AfterEach void tearDown() { persistence.close(); servlet.close(); }
    @Test void shouldSaveAssociation_whenBothCodesAreValid() {
        action.setActionMode("edit"); action.setPrimaryCode("00100"); action.setSecondaryCode("11000");
        action.execute(); verify(dao).saveOrUpdateServiceCodeAssociation("00100", "11000");
    }
    @ParameterizedTest @ValueSource(strings = {"", "invalid"})
    void shouldRejectAssociation_whenPrimaryCodeIsInvalid(String code) {
        action.setActionMode("edit"); action.setPrimaryCode(code); action.setSecondaryCode("11000");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(action.hasActionErrors()).isTrue();
        verify(dao, never()).saveOrUpdateServiceCodeAssociation(any(), any());
        assertThat(request.getAttribute("list")).isNotNull(); assertThat(response.getRedirectedUrl()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"", "invalid"})
    void shouldRejectAssociation_whenSecondaryCodeIsInvalid(String code) {
        action.setActionMode("edit"); action.setPrimaryCode("00100"); action.setSecondaryCode(code);
        assertThat(action.execute()).isEqualTo("success"); assertThat(action.hasActionErrors()).isTrue();
        verify(dao, never()).saveOrUpdateServiceCodeAssociation(any(), any());
    }
    @Test void shouldDeleteAssociation_whenDeleteIsRequested() {
        action.setActionMode("delete"); action.setId("77"); action.execute();
        verify(dao).deleteServiceCodeAssociation("77");
    }
    @Test void shouldRenderList_withoutWritingOnDefaultView() {
        request.setMethod("GET"); assertThat(action.execute()).isEqualTo("success");
        verify(dao).getServiceCodeAssociactions();
        verify(dao, never()).saveOrUpdateServiceCodeAssociation(any(), any());
        verify(dao, never()).deleteServiceCodeAssociation(any());
    }
    @ParameterizedTest @ValueSource(strings = {"edit", "delete"})
    void shouldRejectMutation_whenRequestedUsingGet(String mode) {
        request.setMethod("GET"); action.setActionMode(mode);
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(dao);
    }
    @Test void shouldRejectUnknownMode_withoutPersistence() {
        action.setActionMode("unknown"); action.execute();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(dao);
    }
    @ParameterizedTest @ValueSource(strings = {"", "bad", "-1"})
    void shouldRejectDeletion_whenIdentifierIsInvalid(String id) {
        action.setActionMode("delete"); action.setId(id); action.execute();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(dao);
    }

    @ParameterizedTest @ValueSource(strings = {"edit", "delete"})
    void shouldRejectMutation_whenBillingAdminRightsAreMissing(String mode) {
        when(security.hasPrivilege(info, "_admin.billing,_admin", "w", null)).thenReturn(false);
        action.setActionMode(mode); action.setPrimaryCode("00100"); action.setSecondaryCode("11000"); action.setId("77");
        assertThatExceptionOfType(SecurityException.class).isThrownBy(action::execute)
            .withMessage("missing required sec object (_admin.billing or _admin)");
        verifyNoInteractions(dao);
    }
    @Test void shouldRenderList_forBillingUserWithoutAdminRights() {
        when(security.hasPrivilege(info, "_admin.billing,_admin", "w", null)).thenReturn(false);
        request.setMethod("GET"); assertThat(action.execute()).isEqualTo("success");
        verify(dao).getServiceCodeAssociactions();
    }
    @Test void shouldExposeErrors_toTheJspRenderer() {
        action.setActionMode("edit"); action.setPrimaryCode("invalid"); action.setSecondaryCode("11000");
        assertThat(action.execute()).isEqualTo("success");
        assertThat((java.util.List<?>) request.getAttribute("actionErrors")).isNotEmpty();
    }
}
