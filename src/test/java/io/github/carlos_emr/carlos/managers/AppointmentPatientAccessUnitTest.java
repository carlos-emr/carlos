/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AppointmentPatientAccessUnitTest {
    @Test void shouldFilterBeforeConversion_andRetainUnassignedSlots() {
        var actor=mock(LoggedInInfo.class);var policy=mock(SecurityInfoManager.class);
        when(policy.hasPrivilegeReadOnly(actor,"_appointment","r",null)).thenReturn(true);
        when(policy.hasPrivilegeReadOnly(actor,"_appointment","r","42")).thenReturn(true);
        when(policy.hasPrivilegeReadOnly(actor,"_appointment","r","43")).thenReturn(true);
        when(policy.isAllowedAccessToPatientRecord(actor,42)).thenReturn(true);
        assertThat(AppointmentPatientAccess.readable(actor,policy,List.of(0,42,43,44,42),Integer::intValue))
                .containsExactly(0,42,42);
        verify(policy,times(1)).isAllowedAccessToPatientRecord(actor,42);
    }
    @Test void shouldPropagateLatePolicyFailure_withoutPartialSuccess() {
        var actor=mock(LoggedInInfo.class);var policy=mock(SecurityInfoManager.class);
        when(policy.hasPrivilegeReadOnly(actor,"_appointment","r",null)).thenReturn(true);
        when(policy.hasPrivilegeReadOnly(actor,"_appointment","r","43")).thenThrow(new IllegalStateException("policy"));
        assertThatThrownBy(() -> AppointmentPatientAccess.readable(actor,policy,List.of(0,43),Integer::intValue))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRequirePatientChart_forWritesWithoutAddingReadAuthority() {
        var actor=mock(LoggedInInfo.class);var policy=mock(SecurityInfoManager.class);
        when(policy.hasPrivilege(actor,"_appointment","w","42")).thenReturn(true);
        assertThatThrownBy(() -> AppointmentPatientAccess.require(actor,policy,42,"w"))
                .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        when(policy.isAllowedAccessToPatientRecord(actor,42)).thenReturn(true);
        AppointmentPatientAccess.require(actor,policy,42,"w");
        AppointmentPatientAccess.require(actor,policy,0,"w");
        verify(policy,never()).hasPrivilege(any(),any(),eq("r"),any());
    }
}
