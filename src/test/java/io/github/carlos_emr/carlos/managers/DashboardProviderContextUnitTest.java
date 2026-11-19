/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.display.DisplayDashboard2Action;
import io.github.carlos_emr.carlos.dashboard.display.DisplayDrilldown2Action;
import io.github.carlos_emr.carlos.dashboard.display.beans.DashboardBean;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DashboardProviderContextUnitTest extends CarlosUnitTestBase {
    private DashboardManagerImpl manager;
    private LoggedInInfo actor;
    private SecurityInfoManager security;
    private ActionContext originalContext;
    private void request(MockHttpServletRequest request) {
        ActionContext.bind(ActionContext.of(new HashMap<>()).withServletRequest(request));
    }
    @BeforeEach void prepare() {
        originalContext=ActionContext.getContext();actor=mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("FAKE-login");
        security=mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(),eq("_dashboardChgUser"),eq(SecurityInfoManager.READ),isNull())).thenReturn(true);
        manager=new DashboardManagerImpl();ReflectionTestUtils.setField(manager,"securityInfoManager",security);
    }
    @AfterEach void restore() {
        if(originalContext==null)ActionContext.clear();else ActionContext.bind(originalContext);
    }
    @Test void shouldKeepSelectionWithinEachRequest_whenTwoWindowsShareOneLogin() {
        var a=new MockHttpServletRequest();var b=new MockHttpServletRequest();
        request(a);manager.setRequestedProviderNo(actor,"FAKE-provider-A");
        request(b);manager.setRequestedProviderNo(actor,"FAKE-provider-B");
        request(a);assertEquals("FAKE-provider-A",manager.getRequestedProviderNo(actor));
        request(b);assertEquals("FAKE-provider-B",manager.getRequestedProviderNo(actor));
    }
    @Test void shouldUseTheWindowsExplicitProvider_afterAnotherWindowChangesProvider() {
        request(new MockHttpServletRequest());manager.setRequestedProviderNo(actor,"FAKE-provider-B");
        var followup=new MockHttpServletRequest();followup.setParameter("dashboardProviderNo","FAKE-provider-A");
        request(followup);assertEquals("FAKE-provider-A",manager.getRequestedProviderNo(actor));
    }
    @Test void shouldNotInheritSelection_whenNewRequestHasNoProvider() {
        request(new MockHttpServletRequest());manager.setRequestedProviderNo(actor,"FAKE-provider-A");
        request(new MockHttpServletRequest());assertNull(manager.getRequestedProviderNo(actor));
    }
    @Test void shouldIsolateConcurrentWindowSelections_withoutGlobalProviderState() throws Exception {
        var ready=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->{
                request(new MockHttpServletRequest());
                try {manager.setRequestedProviderNo(actor,"FAKE-provider-A");ready.await(10,TimeUnit.SECONDS);return manager.getRequestedProviderNo(actor);}
                finally {ActionContext.clear();}
            });
            var b=workers.submit(()->{
                request(new MockHttpServletRequest());
                try {manager.setRequestedProviderNo(actor,"FAKE-provider-B");ready.await(10,TimeUnit.SECONDS);return manager.getRequestedProviderNo(actor);}
                finally {ActionContext.clear();}
            });
            assertEquals("FAKE-provider-A",a.get(15,TimeUnit.SECONDS));
            assertEquals("FAKE-provider-B",b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void shouldReturnNoSelection_whenActorIsAbsent() {
        request(new MockHttpServletRequest());assertNull(manager.getRequestedProviderNo(null));
    }
    @Test void shouldKeepExistingNoSelectionBehavior_forAnUnusedRequest() {
        request(new MockHttpServletRequest());assertNull(manager.getRequestedProviderNo(actor));
    }
    @Test void shouldKeepNoSelectionOutsideAnHttpRequest_withoutRetainingState() {
        ActionContext.clear();manager.setRequestedProviderNo(actor,"FAKE-provider-A");
        assertNull(manager.getRequestedProviderNo(actor));
        ActionContext.bind(ActionContext.of());manager.setRequestedProviderNo(actor,"FAKE-provider-B");
        assertNull(manager.getRequestedProviderNo(actor));
    }
    @Test void shouldIgnoreASelectionWithoutAnActor() {
        request(new MockHttpServletRequest());manager.setRequestedProviderNo(null,"FAKE-provider-A");
        assertNull(manager.getRequestedProviderNo(actor));
    }
    @ParameterizedTest @NullAndEmptySource
    void shouldIgnoreSelection_whenLoginIdentityIsMissing(String identity) {
        when(actor.getLoggedInProviderNo()).thenReturn(identity);request(new MockHttpServletRequest());
        manager.setRequestedProviderNo(actor,"FAKE-provider-A");assertNull(manager.getRequestedProviderNo(actor));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  "})
    void shouldClearOnlyTheCurrentRequestSelection_whenItsValueIsBlank(String value) {
        var request=new MockHttpServletRequest();request.setParameter("dashboardProviderNo","FAKE-provider-A");
        request(request);manager.setRequestedProviderNo(actor,value);assertNull(manager.getRequestedProviderNo(actor));
    }
    @Test void shouldAllowOwnProvider_withoutPermissionToSwitchProviders() {
        when(security.hasPrivilege(any(),eq("_dashboardChgUser"),eq(SecurityInfoManager.READ),isNull())).thenReturn(false);
        var request=new MockHttpServletRequest();request.setParameter("dashboardProviderNo","FAKE-login");request(request);
        assertEquals("FAKE-login",manager.getRequestedProviderNo(actor));verifyNoInteractions(security);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldRefuseAnotherProvider_whenSwitchPermissionIsAbsentOrRevoked(boolean selectedInRequest) {
        var request=new MockHttpServletRequest();request(request);
        if(selectedInRequest)manager.setRequestedProviderNo(actor,"FAKE-provider-A");
        else request.setParameter("dashboardProviderNo","FAKE-provider-A");
        when(security.hasPrivilege(any(),eq("_dashboardChgUser"),eq(SecurityInfoManager.READ),isNull())).thenReturn(false);
        assertThrows(SecurityException.class,()->manager.getRequestedProviderNo(actor));
    }
    @Test void shouldPreferTheRequestSelection_whenItsProviderChooserHasJustChanged() {
        var request=new MockHttpServletRequest();request.setParameter("dashboardProviderNo","FAKE-provider-A");request(request);
        manager.setRequestedProviderNo(actor,"FAKE-provider-B");assertEquals("FAKE-provider-B",manager.getRequestedProviderNo(actor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "blank", "chooser", "followup"})
    void shouldKeepTheHeadingSeparateFromTheQueryOverride_whenOpeningDashboardAndDrilldown(String selection) {
        String provider = (selection.equals("default") || selection.equals("blank")) ? null : "FAKE-provider-A";
        Provider loggedInProvider = new Provider();
        loggedInProvider.setProviderNo("FAKE-login");
        when(actor.getLoggedInProvider()).thenReturn(loggedInProvider);
        Provider selectedProvider = new Provider();
        selectedProvider.setProviderNo("FAKE-provider-A");
        when(security.hasPrivilege(actor, "_dashboardDisplay", SecurityInfoManager.READ, null)).thenReturn(true);
        when(security.hasPrivilege(actor, "_dashboardDrilldown", SecurityInfoManager.READ, null)).thenReturn(true);
        when(security.hasPrivilege(actor, "_dxresearch", SecurityInfoManager.WRITE, null)).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        var providerManager = createAndRegisterMock(ProviderManager2.class);
        when(providerManager.getProvider(actor, "FAKE-provider-A")).thenReturn(selectedProvider);
        var dashboards = spy(manager);
        registerMock(DashboardManager.class, dashboards);
        doReturn(mock(DashboardBean.class)).when(dashboards).getDashboard(actor, 11);
        if (provider == null) {
            doReturn(mock(DrilldownBean.class)).when(dashboards).getDrilldownData(actor, 22, "null");
        } else {
            doReturn(mock(DrilldownBean.class)).when(dashboards).getDrilldownData(actor, 22, provider, "null");
        }
        var dashboardRequest = new MockHttpServletRequest();
        dashboardRequest.setParameter("dashboardId", "11");
        if (selection.equals("chooser")) {
            dashboardRequest.setParameter("providerNo", provider);
        } else if (selection.equals("blank")) {
            dashboardRequest.setParameter("providerNo", "  ");
        } else if (selection.equals("followup")) {
            dashboardRequest.setParameter("dashboardProviderNo", provider);
        }
        LoggedInInfo.setLoggedInInfoIntoSession(dashboardRequest.getSession(), actor);
        request(dashboardRequest);

        assertEquals("success", new DisplayDashboard2Action().execute());
        Provider heading = provider == null ? loggedInProvider : selectedProvider;
        assertSame(heading, dashboardRequest.getAttribute("preferredProvider"));
        assertEquals(provider, dashboardRequest.getAttribute("dashboardProviderNo"));

        var drilldownRequest = new MockHttpServletRequest();
        drilldownRequest.setSession(dashboardRequest.getSession());
        drilldownRequest.setParameter("indicatorTemplateId", "22");
        String dashboardOverride = (String) dashboardRequest.getAttribute("dashboardProviderNo");
        if (dashboardOverride != null) {
            drilldownRequest.setParameter("dashboardProviderNo", dashboardOverride);
        }
        request(drilldownRequest);

        assertEquals("success", new DisplayDrilldown2Action().execute());
        assertSame(heading, drilldownRequest.getAttribute("preferredProvider"));
        assertEquals(provider, drilldownRequest.getAttribute("dashboardProviderNo"));
        if (provider == null) {
            verify(dashboards).getDrilldownData(actor, 22, "null");
        } else {
            verify(dashboards).getDrilldownData(actor, 22, provider, "null");
        }
    }

}
