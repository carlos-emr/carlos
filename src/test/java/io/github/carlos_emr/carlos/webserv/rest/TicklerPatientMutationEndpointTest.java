/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import jakarta.ws.rs.client.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
@Tag("unit")
class TicklerPatientMutationEndpointTest extends CarlosRestTestBase {
    @Mock SecurityInfoManager security;
    @Mock TicklerManager ticklers;
    @Override protected Object getServiceBean() {
        var service=new TicklerWebService(); injectDependency(service,"securityInfoManager",security);
        injectDependency(service,"ticklerManager",ticklers); return service;
    }
    @BeforeEach void grantFirstPatient() {
        when(security.hasPrivilege(eq(mockLoggedInInfo),eq("_tickler"),anyString(),isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(mockLoggedInInfo),eq("_tickler"),anyString(),eq("42"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(true);
        when(ticklers.getTicklerDemographicNoForUpdate(mockLoggedInInfo,7)).thenReturn(42);
        when(ticklers.getTicklerDemographicNoForUpdate(mockLoggedInInfo,8)).thenReturn(43);
    }
    private int post(String path,String json) {try(var response=request().path("/tickler/"+path).post(Entity.json(json))) {return response.getStatus();}}
    @Test void shouldValidateEveryPatient_beforeCompletingAnyTickler() {
        assertThat(post("complete","{\"ticklers\":[7,8]}")).isEqualTo(403);
        verify(ticklers,never()).completeTickler(any(),any(),any());
    }
    @Test void shouldValidateEveryPatient_beforeDeletingAnyTickler() {
        assertThat(post("delete","{\"ticklers\":[7,8]}")).isEqualTo(403);
        verify(ticklers,never()).deleteTickler(any(),any(),any());
    }
    @Test void shouldRetainAuthorizedBulkWrites() {
        assertThat(post("complete","{\"ticklers\":[7]}")).isEqualTo(200);
        verify(ticklers).completeTickler(eq(mockLoggedInInfo),eq(7),any());
    }
    @Test void shouldDenyBlockedCharts_beforeAnyMutation() {
        when(security.isAllowedAccessToPatientRecord(mockLoggedInInfo,42)).thenReturn(false);
        assertThat(post("delete","{\"ticklers\":[7]}")).isEqualTo(403);
        verify(ticklers,never()).deleteTickler(any(),any(),any());
    }
    @Test void shouldDenyUpdate_beforeLoadingOrMutatingClinicalData() {
        assertThat(post("update","{\"id\":8}")).isEqualTo(403);
        verify(ticklers,never()).getTickler(any(),anyInt()); verify(ticklers,never()).updateTickler(any(),any());
    }
    @Test void shouldRejectMissingTicklers_beforeAnyMutation() {
        when(ticklers.getTicklerDemographicNoForUpdate(mockLoggedInInfo,9)).thenReturn(null);
        assertThat(post("complete","{\"ticklers\":[7,9]}")).isEqualTo(404);
        verify(ticklers,never()).completeTickler(any(),any(),any());
    }
    @Test void shouldDenyCreationBeforeProgramLookupOrWrites() {
        assertThat(post("add","{\"demographicNo\":43,\"message\":\"retained private content\"}")).isEqualTo(403);
        verify(ticklers,never()).addTickler(any(),any());
    }
    @Test void shouldPropagatePolicyFailure_insteadOfCompletingAPartialBatch() {
        when(security.hasPrivilege(mockLoggedInInfo,"_tickler","u","43")).thenThrow(new IllegalStateException("Synthetic policy failure"));
        assertThatThrownBy(() -> post("complete","{\"ticklers\":[7,8]}"))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        verify(ticklers,never()).completeTickler(any(),any(),any());
    }
}
