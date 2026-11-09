/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.util.PatientApiPages;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
@Tag("unit")
class PatientApiPagesUnitTest {
    private final SecurityInfoManager security=mock(SecurityInfoManager.class);
    private final LoggedInInfo actor=mock(LoggedInInfo.class);
    private void allow(int patient) {
        when(security.hasPrivilegeReadOnly(actor,"_tickler","r",Integer.toString(patient))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor,patient)).thenReturn(true);
    }
    @Test void shouldContinuePastBlockedBatches_andPageAndCountOnlyReadableRows() {
        var source=IntStream.rangeClosed(1,1203).boxed().toList(); allow(1201);allow(1202);allow(1203);
        var calls=new AtomicInteger();
        var page=PatientApiPages.read(actor,security,"_tickler",1,1,(offset,limit) -> {
            calls.incrementAndGet(); assertThat(limit).isEqualTo(500);
            return source.subList(offset,Math.min(source.size(),offset+limit));
        },id -> id,id -> id);
        assertThat(page.rows()).containsExactly(1202); assertThat(page.total()).isEqualTo(3);
        assertThat(calls.get()).isEqualTo(3); verify(security,never()).hasPrivilege(any(),any(),any(),anyString());
    }
    @Test void shouldRetainZeroLimitAndEmptyAuthorizedPages_withAccurateTotals() {
        allow(1);allow(2);
        var all=PatientApiPages.read(actor,security,"_tickler",0,0,(offset,limit) -> List.of(1,2),id -> id,id -> id);
        assertThat(all.rows()).containsExactly(1,2);assertThat(all.total()).isEqualTo(2);
        var beyond=PatientApiPages.read(actor,security,"_tickler",2,1,(offset,limit) -> List.of(1,2),id -> id,id -> id);
        assertThat(beyond.rows()).isEmpty(); assertThat(beyond.total()).isEqualTo(2);
    }
    @Test void shouldExcludeBlockedCharts_andPropagatePolicyFailures() {
        allow(1); when(security.isAllowedAccessToPatientRecord(actor,1)).thenReturn(false);
        assertThat(PatientApiPages.read(actor,security,"_tickler",0,1,(offset,limit) -> List.of(1),id -> id,id -> id).total()).isZero();
        when(security.hasPrivilegeReadOnly(actor,"_tickler","r","1")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(() -> PatientApiPages.read(actor,security,"_tickler",0,1,(offset,limit) -> List.of(1),id -> id,id -> id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldRefuseMalformedAndNonadvancingBackendPages() {
        assertThatThrownBy(() -> PatientApiPages.read(actor,security,"_tickler",0,1,(offset,limit) -> null,id -> 1,id -> id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PatientApiPages.read(actor,security,"_tickler",0,1,(offset,limit) -> List.of(1),id -> null,id -> id)).isInstanceOf(IllegalStateException.class);
        var repeated=IntStream.rangeClosed(1,500).boxed().toList();
        assertThatThrownBy(() -> PatientApiPages.read(actor,security,"_tickler",0,1,(offset,limit) -> repeated,id -> id,id -> id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("did not advance");
    }
}
