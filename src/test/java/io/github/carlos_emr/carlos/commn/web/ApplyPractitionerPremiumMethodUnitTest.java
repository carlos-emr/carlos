/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
class ApplyPractitionerPremiumMethodUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private BillingONPremiumDao premiums;
    private ProviderDao providers;
    private SecurityInfoManager security;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        premiums = mock(BillingONPremiumDao.class);
        providers = mock(ProviderDao.class);
        security = mock(SecurityInfoManager.class);
        registerMock(BillingONPremiumDao.class, premiums);
        registerMock(ProviderDao.class, providers);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(login, "_billing", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() { servlet.close(); }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRefuseBeforeReadingPremiums_whenMethodIsNotPost(String method) {
        request.setMethod(method);
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(premiums, providers);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldApplyCheckedAndUncheckedState_whenAuthorizedPostIsSubmitted(boolean selected) {
        request.setMethod("POST");
        request.setParameter("rano", "11");
        request.setParameter("providerNo7", "owner");
        if (selected) request.setParameter("choosePremium7", "Y");
        BillingONPremium premium = new BillingONPremium();
        ReflectionTestUtils.setField(premium, "id", 7);
        premium.setStatus(!selected);
        when(premiums.getRAPremiumsByRaHeaderNo(11)).thenReturn(List.of(premium));
        when(providers.getProvider("owner")).thenReturn(new Provider());
        assertThat(new ApplyPractitionerPremium2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(premium.getStatus()).isEqualTo(selected);
        assertThat(premium.getProviderNo()).isEqualTo(selected ? "owner" : null);
        verify(premiums).merge(premium);
    }

    @Test
    void shouldRefusePremiumChanges_whenBillingWriteIsDenied() {
        request.setMethod("POST");
        request.setParameter("rano", "11");
        when(security.hasPrivilege(login, "_billing", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> new ApplyPractitionerPremium2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(premiums, providers);
    }
}
