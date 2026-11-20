/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prev.reports;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
@Tag("unit")
class PreventionReportPatientPolicyUnitTest {
    private final SecurityInfoManager permissions=mock(SecurityInfoManager.class);
    private final LoggedInInfo actor=mock(LoggedInInfo.class);
    @Test void shouldApplyEveryPatientBoundary_inBoundedReadOnlyBatches(){
        when(permissions.getReadableDemographicIds(eq(actor),any())).thenAnswer(call->{Collection<Integer> ids=call.getArgument(1);assertThat(ids).hasSizeLessThanOrEqualTo(500);return new HashSet<>(ids.stream().filter(id->id>=1201).toList());});
        when(permissions.hasPrivilegeReadOnly(eq(actor),eq("_prevention"),eq("r"),anyString())).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(actor,1201)).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(actor,1203)).thenReturn(true);
        when(permissions.hasPrivilegeReadOnly(actor,"_prevention","r","1203")).thenReturn(false);
        assertThat(PreventionReportAccess.readableCandidates(actor,permissions,IntStream.rangeClosed(1,1203).boxed().toList())).containsExactly(1201);
        verify(permissions,times(3)).getReadableDemographicIds(eq(actor),any());
        verify(permissions,never()).hasPrivilege(any(),any(),any(),anyString());
    }
    @Test void shouldPropagateLatePolicyFailure_withoutReturningAPartialAuthorizedSet(){
        var calls=new AtomicInteger();
        when(permissions.getReadableDemographicIds(eq(actor),any())).thenAnswer(call->{if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic policy failure");Collection<Integer> ids=call.getArgument(1);return new HashSet<>(ids);});
        when(permissions.hasPrivilegeReadOnly(any(),any(),any(),any())).thenReturn(true);
        when(permissions.isAllowedAccessToPatientRecord(any(),any())).thenReturn(true);
        assertThatThrownBy(()->PreventionReportAccess.readableCandidates(actor,permissions,IntStream.rangeClosed(1,501).boxed().toList())).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void shouldRefuseMalformedCandidates_beforePolicyEvaluation(){
        assertThatThrownBy(()->PreventionReportAccess.readableCandidates(actor,permissions,Arrays.asList(1,null))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(permissions);
    }
    @Test void shouldRefuseMissingPolicyResults_insteadOfReturningAnEmptySuccess(){
        when(permissions.getReadableDemographicIds(eq(actor),any())).thenReturn(null);
        assertThatThrownBy(()->PreventionReportAccess.readableCandidates(actor,permissions,List.of(1))).isInstanceOf(IllegalStateException.class);
    }
}
