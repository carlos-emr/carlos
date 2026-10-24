/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.admin.lookUpLists.LookupListManager2Action;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.data.DemographicSets;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Builder;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Loader;
import io.github.carlos_emr.carlos.report.data.RptDemographicQuery2Saver;
import io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Guards verified GET-write regressions without disabling intentional POST writes. */
@Tag("unit")
class ReadMethodWriteGuardUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LookupListManager lists;
    private Object previousLookupListManager;
    private LoggedInInfo login;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        lists = mock(LookupListManager.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(LookupListManager.class, lists);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        previousLookupListManager = ReflectionTestUtils.getField(LookupListManager2Action.class, "lookupListManager");
        ReflectionTestUtils.setField(LookupListManager2Action.class, "lookupListManager", lists);
        when(security.hasPrivilege(eq(login), anyString(), anyString(), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(LookupListManager2Action.class, "lookupListManager", previousLookupListManager);
        servlet.close();
    }

    @ParameterizedTest
    @CsvSource({"GET, Save Query", "HEAD, Save Query", "PUT, Save Query", "DELETE, Save Query",
            "GET, Run Query And Save to Patient Set", "HEAD, Run Query And Save to Patient Set"})
    void shouldRejectReportWritesBeforeConstructingDataAccess_whenMethodIsNotPost(String method, String query)
            throws Exception {
        request.setMethod(method);
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery(query);
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class);
             var builder = mockConstruction(RptDemographicQuery2Builder.class);
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertPostRequired();
            assertThat(saver.constructed()).isEmpty();
            assertThat(builder.constructed()).isEmpty();
            assertThat(sets.constructed()).isEmpty();
        }
    }

    @Test
    void shouldSaveFavourite_whenRequestIsPost() throws Exception {
        request.setMethod("POST");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Save Query");
        try (var saver = mockConstruction(RptDemographicQuery2Saver.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(saver.constructed()).hasSize(1);
            verify(saver.constructed().getFirst()).saveQuery(action.getModel());
        }
    }

    @Test
    void shouldSavePatientSet_whenRequestIsPost() throws Exception {
        request.setMethod("POST");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Run Query And Save to Patient Set");
        action.getModel().setSelect(new String[]{"demographic_no"});
        action.getModel().setSetName("owned-set");
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(List.of("41")));
        try (var builder = mockConstruction(RptDemographicQuery2Builder.class,
                (mock, context) -> when(mock.buildQuery(login, action.getModel())).thenReturn(rows));
             var sets = mockConstruction(DemographicSets.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(sets.constructed()).hasSize(1);
            verify(sets.constructed().getFirst()).addDemographicSet("owned-set", List.of("41"));
        }
    }

    @Test
    void shouldRetainReportReadPaths_whenRequestIsGet() throws Exception {
        request.setMethod("GET");
        var action = new RptDemographicReport2Action();
        action.getModel().setQuery("Run Query");
        try (var builder = mockConstruction(RptDemographicQuery2Builder.class,
                (mock, context) -> when(mock.buildQuery(login, action.getModel())).thenReturn(new ArrayList<>()));
             var loader = mockConstruction(RptDemographicQuery2Loader.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(builder.constructed().getFirst()).buildQuery(login, action.getModel());
            action.getModel().setQuery("Load Query");
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verify(loader.constructed().getFirst()).queryLoader(action.getModel());
        }
    }

    @ParameterizedTest
    @CsvSource({"GET, order", "HEAD, order", "GET, add", "HEAD, add", "GET, remove", "HEAD, remove"})
    void shouldRejectLookupWritesBeforeManagerAccess_whenMethodIsReadOnly(String method, String operation) {
        request.setMethod(method);
        request.setParameter("method", operation);
        request.setParameter("lookupListItemId", "7");
        request.setParameter("lookupListItemDisplayOrder", "2");
        request.setParameter("lookupListId", "3");
        request.setParameter("lookupListItemLabel", "Owned label");
        assertThat(new LookupListManager2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertPostRequired();
        verifyNoInteractions(lists);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order", "add", "remove"})
    void shouldRetainLookupMutations_whenRequestIsPost(String operation) {
        request.setMethod("POST");
        request.setParameter("method", operation);
        request.setParameter("lookupListItemId", "7");
        request.setParameter("lookupListItemDisplayOrder", "2");
        request.setParameter("lookupListId", "3");
        request.setParameter("lookupListItemLabel", "Owned label");
        when(lists.findLookupListItemsByLookupListId(login, 3)).thenReturn(List.of());
        assertThat(new LookupListManager2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        switch (operation) {
            case "order" -> verify(lists).updateLookupListItemDisplayOrder(login, 7, 2);
            case "remove" -> verify(lists).removeLookupListItem(login, 7);
            case "add" -> verify(lists).addLookupListItem(eq(login), argThat(item ->
                    "Owned label".equals(item.getLabel()) && item.getLookupListId() == 3));
            default -> throw new AssertionError(operation);
        }
    }

    @Test
    void shouldRetainLookupViews_whenRequestIsGet() {
        request.setMethod("GET");
        request.setParameter("method", "manage");
        var action = new LookupListManager2Action();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(lists).findAllActiveLookupLists(login);
        request.removeParameter("method");
        request.setParameter("listName", "owned");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(lists).findLookupListByName(login, "owned");
    }

    @ParameterizedTest
    @CsvSource({"GET, UpdateResident", "HEAD, UpdateResident", "GET, UpdateNurse", "HEAD, UpdateNurse",
            "GET, UpdateMidwife", "HEAD, UpdateMidwife", "GET, UpdateMrp", "HEAD, UpdateMrp"})
    void shouldRefuseJspForwarding_whenProviderUpdateUsesReadMethod(String method, String operation) {
        request.setMethod(method);
        request.setParameter("update", operation);
        assertThat(new UpdateDemographicProvider2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertPostRequired();
    }

    @Test
    void shouldAllowProviderViewButRefuseMutation_whenUserHasReadOnlyPrivileges() {
        when(security.hasPrivilege(eq(login), anyString(), eq("w"), isNull())).thenReturn(false);
        request.setMethod("GET");
        var action = new UpdateDemographicProvider2Action();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        request.setMethod("POST");
        request.setParameter("update", "UpdateMrp");
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_admin.misc", "_admin"})
    void shouldForwardProviderUpdate_whenPostHasWritePrivilege(String privilege) {
        when(security.hasPrivilege(eq(login), anyString(), eq("w"), isNull())).thenReturn(false);
        when(security.hasPrivilege(login, privilege, "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.setParameter("update", "UpdateMrp");
        assertThat(new UpdateDemographicProvider2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    private void assertPostRequired() {
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
    }
}
