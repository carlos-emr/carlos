/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.dashboard.display;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.admin.ExportResults2Action;
import io.github.carlos_emr.carlos.dashboard.display.beans.DrilldownBean;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.xml.sax.InputSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual shared action, request-local provider validation, form model and subsequent export action. */
@Tag("unit")
class SharedDashboardProviderContextUnitTest extends CarlosUnitTestBase {
    private DashboardManagerImpl manager;
    private SecurityInfoManager security;
    private ProviderDao providerDao;
    private ProviderManager2 providers;
    private Provider heading;
    private LoggedInInfo actor;
    private IndicatorTemplate indicator;
    private ActionContext original;
    @BeforeEach void prepare() {
        original=ActionContext.getContext();actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("FAKE-login");
        security=mock(SecurityInfoManager.class);when(security.hasPrivilege(eq(actor),anyString(),anyString(),isNull())).thenReturn(true);
        manager=spy(new DashboardManagerImpl());ReflectionTestUtils.setField(manager,"securityInfoManager",security);
        providerDao=mock(ProviderDao.class);when(providerDao.providerExists("FAKE-selected")).thenReturn(true);when(providerDao.providerExists("FAKE-login")).thenReturn(true);
        providers=mock(ProviderManager2.class);heading=mock(Provider.class);when(providers.getProvider(eq(actor),anyString())).thenReturn(heading);
        indicator=new IndicatorTemplate();indicator.setId(17);
        doReturn(List.of(indicator)).when(manager).getIndicatorLibrary(eq(actor),anyBoolean());
        doReturn(new DrilldownBean()).when(manager).getDrilldownData(eq(actor),eq(17),anyString(),anyString());
        doReturn("FAKE-selected-only\n").when(manager).exportDrilldownQueryResultsToCSV(actor,"FAKE-selected",17);
        doReturn("FAKE-template-default\n").when(manager).exportDrilldownQueryResultsToCSV(actor,17);
        registerMock(DashboardManager.class,manager);registerMock(SecurityInfoManager.class,security);registerMock(ProviderDao.class,providerDao);registerMock(ProviderManager2.class,providers);registerMock(DemographicExtDao.class,mock(DemographicExtDao.class));
    }
    @AfterEach void restore() {if(original==null)ActionContext.clear();else ActionContext.bind(original);}
    private MockHttpServletRequest request(String selected) {
        var request=new MockHttpServletRequest();LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),actor);if(selected!=null)request.setParameter("providerNo",selected);request.setParameter("method","getDrilldownBySharedMetricSetName");request.setParameter("sharedMetricSetName","FAKE-metric");ActionContext.bind(ActionContext.of(new HashMap<>()).withServletRequest(request));return request;
    }
    private DisplayDrilldown2Action shared(MockHttpServletRequest request) {
        try(var servlet=mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            return new DisplayDrilldown2Action() {
                @Override protected IndicatorTemplate findIndicatorTemplateBySharedMetricSetName(LoggedInInfo actor,List<IndicatorTemplate> templates,String name) {return indicator;}
                @Override protected IndicatorTemplate findIndicatorTemplateBySharedMetricSetNameAndMatricLabel(LoggedInInfo actor,List<IndicatorTemplate> templates,String name,String label) {return indicator;}
            };
        }
    }
    static Stream<Arguments> scopes() {return Stream.of("all","%","loggedInProvider").flatMap(alias->Stream.of(Arguments.of(alias,false),Arguments.of(alias,true)));}
    @ParameterizedTest @MethodSource("scopes")
    void shouldExportDisplayedProvider_fromSharedWholeMetricAndSlice(String alias,boolean slice) throws Exception {
        var request=request("FAKE-selected");if(slice)request.setParameter("sharedMetricSetLable","FAKE-slice");
        assertEquals("success",shared(request).execute());
        var selected=(String)request.getAttribute("dashboardProviderNo");
        String xml="<indicatorTemplateXML><heading/><indicatorQuery><params><parameter id='provider' name='Provider' value='"+alias+"'/></params></indicatorQuery><drillDownQuery><params><parameter id='provider' name='Provider' value='"+alias+"'/></params></drillDownQuery></indicatorTemplateXML>";
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var template=new IndicatorTemplateXML(actor,factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));template.setProviderNo(selected);
        var post=new MockHttpServletRequest();post.setMethod("POST");post.setParameter("indicatorId","17");post.setParameter("dashboardProviderNo",selected==null?"":selected);LoggedInInfo.setLoggedInInfoIntoSession(post.getSession(),actor);var response=new MockHttpServletResponse();ActionContext.bind(ActionContext.of(new HashMap<>()).withServletRequest(post));
        ExportResults2Action export;
        try(var servlet=mockStatic(ServletActionContext.class)) {servlet.when(ServletActionContext::getRequest).thenReturn(post);servlet.when(ServletActionContext::getResponse).thenReturn(response);export=new ExportResults2Action();}
        assertEquals("none",export.execute());
        assertAll(()->assertEquals("FAKE-selected",selected),()->assertSame(heading,request.getAttribute("preferredProvider")),()->assertArrayEquals(new String[]{"FAKE-selected"},template.getDrilldownParameters(null).get(0).getValue()),()->assertEquals("FAKE-selected-only\n",response.getContentAsString()));
        verify(manager).getDrilldownData(actor,17,"FAKE-selected",slice?"FAKE-slice":"null");verify(manager,never()).exportDrilldownQueryResultsToCSV(actor,17);
    }
    @Test void shouldRefuseSharedSelection_beforeQueryWhenSwitchPermissionIsMissing() {
        when(security.hasPrivilege(actor,"_dashboardChgUser",SecurityInfoManager.READ,null)).thenReturn(false);var request=request("FAKE-selected");assertThrows(SecurityException.class,()->shared(request).execute());verify(manager,never()).getIndicatorLibrary(any(),anyBoolean());
    }
    @Test void shouldAllowOwnSharedSelection_withoutSwitchPermission() {
        when(security.hasPrivilege(actor,"_dashboardChgUser",SecurityInfoManager.READ,null)).thenReturn(false);var request=request("FAKE-login");assertEquals("success",shared(request).execute());assertEquals("FAKE-login",request.getAttribute("dashboardProviderNo"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","FAKE-missing"})
    void shouldRefuseMissingSharedProvider_withoutAnExportModel(String selected) {
        var request=request(selected);assertEquals("error",shared(request).execute());assertNull(request.getAttribute("dashboardProviderNo"));verify(manager,never()).getDrilldownData(any(),anyInt(),anyString(),anyString());
    }
}
