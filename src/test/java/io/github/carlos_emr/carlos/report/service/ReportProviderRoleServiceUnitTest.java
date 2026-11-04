/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class ReportProviderRoleServiceUnitTest {
    SecurityInfoManager security;
    SecuserroleDao roles;
    ProviderDao providers;
    LoggedInInfo user;
    ReportProviderRoleService service;
    @BeforeEach void prepare() {
        security=mock(SecurityInfoManager.class); roles=mock(SecuserroleDao.class);
        providers=mock(ProviderDao.class); user=mock(LoggedInInfo.class);
        service=new ReportProviderRoleService(security,roles,providers);
    }
    void allow() {
        when(security.hasPrivilege(user,"_admin.userAdmin","w",null)).thenReturn(true);
        when(providers.getProvider("999998")).thenReturn(new Provider());
    }
    @Test void shouldRefuseAllWrites_whenCallerHasOnlyReportRead() {
        assertThatThrownBy(()->service.update(user,"999998","doctor")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.add(user,Map.of("999998","doctor"))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(roles,providers);
    }
    @ParameterizedTest @ValueSource(strings={"admin","doctor,admin","", "Doctor", "<script>"})
    void shouldRefuseInvalidRolesBeforeReadingAssignments(String role) {
        allow();
        assertThatThrownBy(()->service.update(user,"999998",role)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roles);
    }
    @Test void shouldUpdateOnlyClinicalAssignments_whenProviderAlsoHasAdminRole() {
        allow();
        Secuserrole clinical=new Secuserrole(); clinical.setId(1); clinical.setRoleName("nurse");
        Secuserrole admin=new Secuserrole(); admin.setId(2); admin.setRoleName("admin");
        when(roles.findByProviderNo("999998")).thenReturn(List.of(clinical,admin));
        service.update(user,"999998","doctor");
        verify(roles).updateRoleName(1,"doctor"); verify(roles,never()).updateRoleName(eq(2),any());
    }
    @Test void shouldValidateEntireBatchBeforeAnyWrite() {
        allow(); var batch=new LinkedHashMap<String,String>();
        batch.put("999998","doctor"); batch.put("999997","admin");
        assertThatThrownBy(()->service.add(user,batch)).isInstanceOf(IllegalArgumentException.class);
        verify(roles,never()).save(any());
    }
    @Test void shouldCreateAllowedRole_whenProviderHasNone() {
        allow(); service.add(user,Map.of("999998","resident"));
        var saved=org.mockito.ArgumentCaptor.forClass(Secuserrole.class);verify(roles).save(saved.capture());
        assertThat(saved.getValue().getRoleName()).isEqualTo("resident");
        assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
    }
    @Test void shouldPreserveExistingAssignments_whenAddingRoles() {
        allow();when(roles.findByProviderNo("999998")).thenReturn(List.of(new Secuserrole()));
        service.add(user,Map.of("999998","doctor"));verify(roles,never()).save(any());
    }
    @Test void shouldRefuseUnknownProvider() {
        when(security.hasPrivilege(user,"_admin","w",null)).thenReturn(true);
        assertThatThrownBy(()->service.add(user,Map.of("0","doctor"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roles);
    }
}
