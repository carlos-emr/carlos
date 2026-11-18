/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.dashboard.display;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.StringReader;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DashboardTemplateProviderDefaultUnitTest extends CarlosUnitTestBase {
    private DashboardManager dashboard;
    private ProviderManager2 providers;
    private LoggedInInfo actor;
    private Provider heading;
    private MockHttpServletRequest request;

    @BeforeEach void prepare() {
        dashboard = mock(DashboardManager.class);
        providers = mock(ProviderManager2.class);
        var security = mock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        heading = mock(Provider.class);
        when(actor.getLoggedInProvider()).thenReturn(heading);
        when(actor.getLoggedInProviderNo()).thenReturn("FAKE-login");
        when(heading.getProviderNo()).thenReturn("FAKE-login");
        when(security.hasPrivilege(eq(actor), anyString(), anyString(), isNull())).thenReturn(true);
        registerMock(DashboardManager.class, dashboard);
        registerMock(ProviderManager2.class, providers);
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));
        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), actor);
    }
    static Stream<Arguments> scopes() {
        return Stream.of("all", "%", "loggedInProvider").flatMap(alias ->
            Stream.of(Arguments.of(alias, null), Arguments.of(alias, "FAKE-selected")));
    }
    private void assertTemplateScope(String alias, String selection) throws Exception {
        assertSame(selection, request.getAttribute("dashboardProviderNo"));
        assertNotNull(request.getAttribute("preferredProvider"));
        String xml = "<indicatorTemplateXML><heading/><indicatorQuery><params>"
            + "<parameter id='provider' name='Provider' value='" + alias + "'/></params></indicatorQuery>"
            + "<drillDownQuery><params><parameter id='provider' name='Provider' value='" + alias
            + "'/></params></drillDownQuery></indicatorTemplateXML>";
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var template = new IndicatorTemplateXML(actor,
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));
        template.setProviderNo((String) request.getAttribute("dashboardProviderNo"));
        String expected = selection != null ? selection : "loggedInProvider".equals(alias) ? "FAKE-login" : "%";
        assertArrayEquals(new String[]{expected}, template.getIndicatorParameters().get(0).getValue());
        assertArrayEquals(new String[]{expected}, template.getDrilldownParameters(null).get(0).getValue());
    }
    @ParameterizedTest @MethodSource("scopes")
    void shouldKeepTemplateDefaultUnlessExplicitlySelected_onDashboard(String alias, String selection) throws Exception {
        when(dashboard.getRequestedProviderNo(actor)).thenReturn(selection);
        when(providers.getProvider(actor, selection)).thenReturn(heading);
        DisplayDashboard2Action action;
        try (var servlet = mockStatic(ServletActionContext.class)) { action = new DisplayDashboard2Action(); }
        action.request = request;
        assertEquals("success", action.execute());
        assertTemplateScope(alias, selection);
    }
    @ParameterizedTest @MethodSource("scopes")
    void shouldKeepTemplateDefaultUnlessExplicitlySelected_onDrilldown(String alias, String selection) throws Exception {
        when(dashboard.getRequestedProviderNo(actor)).thenReturn(selection);
        when(providers.getProvider(actor, selection)).thenReturn(heading);
        when(dashboard.getDrilldownData(actor, 0, "null")).thenReturn(new DrilldownBean());
        when(dashboard.getDrilldownData(actor, 0, selection, "null")).thenReturn(new DrilldownBean());
        DisplayDrilldown2Action action;
        try (var servlet = mockStatic(ServletActionContext.class)) { action = new DisplayDrilldown2Action(); }
        action.request = request;
        assertEquals("success", action.execute());
        assertTemplateScope(alias, selection);
        if (selection == null) verify(dashboard).getDrilldownData(actor, 0, "null");
        else verify(dashboard).getDrilldownData(actor, 0, selection, "null");
    }
}
