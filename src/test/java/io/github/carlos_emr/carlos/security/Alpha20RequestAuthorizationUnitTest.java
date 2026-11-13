/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.security;

import io.github.carlos_emr.carlos.admin.service.DemographicProviderReassignmentService;
import io.github.carlos_emr.carlos.admin.web.UpdateDemographicProvider2Action;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.gate.ReportProviderRoles2Action;
import io.github.carlos_emr.carlos.report.service.ReportProviderRoleService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class Alpha20RequestAuthorizationUnitTest extends CarlosUnitTestBase {
    MockHttpServletRequest request; MockHttpServletResponse response;
    SecurityInfoManager security; LoggedInInfo user;
    ReportProviderRoleService roles; DemographicProviderReassignmentService reassignments;
    MockedStatic<ServletActionContext> servlet;
    @BeforeEach void prepare() {
        request=new MockHttpServletRequest("POST","/report/ViewReportonbilledvisitprovider");
        response=new MockHttpServletResponse();user=mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),user);
        security=mock(SecurityInfoManager.class);roles=mock(ReportProviderRoleService.class);
        reassignments=mock(DemographicProviderReassignmentService.class);
        registerMock(SecurityInfoManager.class,security);registerMock(ReportProviderRoleService.class,roles);
        registerMock(DemographicProviderReassignmentService.class,reassignments);
        when(security.hasPrivilege(user,"_report","r",null)).thenReturn(true);
        when(security.hasPrivilege(user,"_admin.misc","r",null)).thenReturn(true);
        servlet=mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }
    @AfterEach void close() {servlet.close();}
    @ParameterizedTest @ValueSource(strings={"GET","HEAD","PUT","DELETE"})
    void shouldRejectNonPostRoleMutation_beforeService(String method) throws Exception {
        request.setMethod(method);request.setParameter("buttonUpdate","Update");
        assertThat(new ReportProviderRoles2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);verifyNoInteractions(roles);
    }
    @Test void shouldKeepReportReadAccess_withoutRoleWriteAccess() throws Exception {
        request.setMethod("GET");assertThat(new ReportProviderRoles2Action().execute()).isEqualTo("success");
        assertThat(request.getAttribute("canManageProviderRoles")).isEqualTo(false);
        verify(roles,never()).update(any(),any(),any());verify(roles,never()).add(any(),any());
    }
    @Test void shouldRejectReportReaderPost_beforeRoleWrite() {
        request.setParameter("buttonUpdate","Update");
        assertThatThrownBy(()->new ReportProviderRoles2Action().execute()).isInstanceOf(SecurityException.class);
        verify(roles,never()).update(any(),any(),any());
    }
    @Test void shouldAllowAuthorizedRoleUpdate() throws Exception {
        when(roles.canManage(user)).thenReturn(true);request.setParameter("buttonUpdate","Update");
        request.setParameter("providerId","999998");request.setParameter("name999998","doctor");
        assertThat(new ReportProviderRoles2Action().execute()).isEqualTo("success");
        verify(roles).update(user,"999998","doctor");
    }
    @ParameterizedTest @ValueSource(strings={"GET","HEAD","PUT","DELETE"})
    void shouldRejectNonPostReassignment_beforeService(String method) throws Exception {
        request.setMethod(method);request.setParameter("update","UpdateResident");
        assertThat(new UpdateDemographicProvider2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);verifyNoInteractions(reassignments);
    }
    @Test void shouldRejectReadOnlyAdministratorPost_beforeReassignment() {
        request.setParameter("update","UpdateMrp");
        assertThatThrownBy(()->new UpdateDemographicProvider2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(reassignments);
    }
    @Test void shouldKeepReadOnlyReassignmentFormAvailable() throws Exception {
        request.setMethod("GET");assertThat(new UpdateDemographicProvider2Action().execute()).isEqualTo("success");
        verifyNoInteractions(reassignments);
    }
    @Test void shouldDispatchAuthorizedReassignment_withCorrectProviderFields() throws Exception {
        when(security.hasPrivilege(user,"_admin.misc","w",null)).thenReturn(true);
        request.setParameter("update","UpdateMrp");request.setParameter("oldcust5","999998");
        request.setParameter("newcust5","999997");request.setParameter("last_name_from","A");request.setParameter("last_name_to","Z");
        when(reassignments.reassign(user,"UpdateMrp","999998","999997","A","Z")).thenReturn(2);
        assertThat(new UpdateDemographicProvider2Action().execute()).isEqualTo("success");
        assertThat(request.getAttribute("reassignedProviderCount")).isEqualTo(2);
    }
}
