/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.managers;

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
        assertThrows(jakarta.ws.rs.ForbiddenException.class,()->manager.getRequestedProviderNo(actor));
    }
    @Test void shouldPreferTheRequestSelection_whenItsProviderChooserHasJustChanged() {
        var request=new MockHttpServletRequest();request.setParameter("dashboardProviderNo","FAKE-provider-A");request(request);
        manager.setRequestedProviderNo(actor,"FAKE-provider-B");assertEquals("FAKE-provider-B",manager.getRequestedProviderNo(actor));
    }

}
