/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7MessageDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7ObrDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.PersistenceException;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins PathNet routing's two layers: an unresolvable provider or patient still stores the lab as
 * unclaimed or unmatched, while a failure to write that routing row propagates so the batch's
 * transaction rolls back.
 *
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class MessageUnitTest extends CarlosUnitTestBase {
    private PatientLabRoutingDao patientRouting;
    private ProviderLabRoutingDao providerRouting;
    private Hl7PidDao pids;
    private Hl7ObrDao obrs;
    private ProviderDao providers;
    private DemographicDaoImpl demographics;

    @BeforeEach
    void setUpDaos() {
        patientRouting = mock(PatientLabRoutingDao.class);
        providerRouting = mock(ProviderLabRoutingDao.class);
        pids = mock(Hl7PidDao.class);
        obrs = mock(Hl7ObrDao.class);
        registerMock(PatientLabRoutingDao.class, patientRouting);
        registerMock(ProviderLabRoutingDao.class, providerRouting);
        registerMock(Hl7PidDao.class, pids);
        registerMock(Hl7ObrDao.class, obrs);
        registerMock(Hl7MessageDao.class, mock(Hl7MessageDao.class));
        providers = mock(ProviderDao.class);
        demographics = mock(DemographicDaoImpl.class);
        registerMock(ProviderDao.class, providers);
        registerMock(DemographicDaoImpl.class, demographics);
        registerMock(DemographicDao.class, mock(DemographicDao.class));
    }

    private static Hl7Obr obr(String orderingProvider, String resultCopiesTo) {
        Hl7Obr obr = new Hl7Obr();
        obr.setOrderingProvider(orderingProvider);
        obr.setResultCopiesTo(resultCopiesTo);
        return obr;
    }

    private static Hl7Pid pid(String patientName) {
        Hl7Pid pid = new Hl7Pid();
        pid.setPatientName(patientName);
        pid.setDateOfBirth(new Date(0));
        pid.setSex("F");
        return pid;
    }

    private static Provider provider(String providerNo) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        return provider;
    }

    @Test
    void shouldRouteToUnmatchedPatient_whenNoPidRecorded() {
        when(pids.findByMessageId(7)).thenReturn(List.of());

        new Message("now").patientRouteReport(7);

        ArgumentCaptor<PatientLabRouting> routing = ArgumentCaptor.forClass(PatientLabRouting.class);
        verify(patientRouting).persist(routing.capture());
        assertThat(routing.getValue().getDemographicNo()).isZero();
        assertThat(routing.getValue().getLabNo()).isEqualTo(7);
        assertThat(routing.getValue().getLabType()).isEqualTo("BCP");
    }

    @Test
    void shouldPropagateFailure_whenUnmatchedPatientRoutingCannotBeWritten() {
        when(pids.findByMessageId(7)).thenReturn(List.of());
        doThrow(new PersistenceException("database unavailable")).when(patientRouting).persist(any());

        // Before, this was logged at debug and the batch committed without the routing row.
        assertThatThrownBy(() -> new Message("now").patientRouteReport(7))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void shouldRouteToUnclaimedProvider_whenNoObrRecorded() {
        when(obrs.findByPid(3)).thenReturn(List.of());

        new Message("now").linkToProvider(7, 3);

        ArgumentCaptor<ProviderLabRoutingModel> routing = ArgumentCaptor.forClass(ProviderLabRoutingModel.class);
        verify(providerRouting).persist(routing.capture());
        assertThat(routing.getValue().getProviderNo()).isEqualTo("0");
        assertThat(routing.getValue().getLabNo()).isEqualTo(7);
        assertThat(routing.getValue().getLabType()).isEqualTo("BCP");
    }

    @Test
    void shouldPropagateFailure_whenUnclaimedProviderRoutingCannotBeWritten() {
        when(obrs.findByPid(3)).thenReturn(List.of());
        doThrow(new PersistenceException("database unavailable")).when(providerRouting).persist(any());

        assertThatThrownBy(() -> new Message("now").linkToProvider(7, 3))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void shouldPropagateFailure_whenObrLookupFails() {
        when(obrs.findByPid(3)).thenThrow(new PersistenceException("database unavailable"));

        // Before, this fell back to provider "0" and the batch committed as if routed.
        assertThatThrownBy(() -> new Message("now").linkToProvider(7, 3))
                .isInstanceOf(PersistenceException.class);
        verify(providerRouting, never()).persist(any());
    }

    @Test
    void shouldRouteToUnclaimedProvider_whenNoMinistryNumberIsKnown() {
        when(obrs.findByPid(3)).thenReturn(List.of(obr("99999^UNKNOWN", "")));
        when(providers.getBillableProvidersByOHIPNo("99999")).thenReturn(List.of());

        try (MockedConstruction<ProviderLabRouting> routers = mockConstruction(ProviderLabRouting.class)) {
            new Message("now").linkToProvider(7, 3);

            // Before, the empty lookup result was routed as provider "" and no unclaimed row was written.
            assertThat(routers.constructed()).isEmpty();
        }
        ArgumentCaptor<ProviderLabRoutingModel> routing = ArgumentCaptor.forClass(ProviderLabRoutingModel.class);
        verify(providerRouting).persist(routing.capture());
        assertThat(routing.getValue().getProviderNo()).isEqualTo("0");
    }

    @Test
    void shouldRouteToEachKnownProvider_whenObrNamesThem() {
        when(obrs.findByPid(3)).thenReturn(List.of(obr("111^ORDERING", "222^COPY~333^NOBODY~111^ORDERING")));
        when(providers.getBillableProvidersByOHIPNo("111")).thenReturn(List.of(provider("P1")));
        when(providers.getBillableProvidersByOHIPNo("222")).thenReturn(List.of(provider("P2")));
        when(providers.getBillableProvidersByOHIPNo("333")).thenReturn(List.of());

        try (MockedConstruction<ProviderLabRouting> routers = mockConstruction(ProviderLabRouting.class)) {
            new Message("now").linkToProvider(7, 3);

            ProviderLabRouting router = routers.constructed().get(0);
            verify(router).routeMagic(7, "P1", "BCP");
            verify(router).routeMagic(7, "P2", "BCP");
            verify(router, never()).routeMagic(7, "", "BCP");
        }
        verify(providerRouting, never()).persist(any());
    }

    @Test
    void shouldPropagateFailure_whenProviderRoutingFails() {
        when(obrs.findByPid(3)).thenReturn(List.of(obr("111^ORDERING", "")));
        when(providers.getBillableProvidersByOHIPNo("111")).thenReturn(List.of(provider("P1")));

        try (MockedConstruction<ProviderLabRouting> routers = mockConstruction(ProviderLabRouting.class,
                (router, context) -> doThrow(new PersistenceException("database unavailable"))
                        .when(router).routeMagic(7, "P1", "BCP"))) {
            // Before, a failed route to the real provider was masked by a successful "0" fallback.
            assertThatThrownBy(() -> new Message("now").linkToProvider(7, 3))
                    .isInstanceOf(PersistenceException.class);
        }
        verify(providerRouting, never()).persist(any());
    }

    @Test
    void shouldPropagateFailure_whenPidLookupFails() {
        when(pids.findByMessageId(7)).thenThrow(new PersistenceException("database unavailable"));

        assertThatThrownBy(() -> new Message("now").patientRouteReport(7))
                .isInstanceOf(PersistenceException.class);
        verify(patientRouting, never()).persist(any());
    }

    @Test
    void shouldRouteToUnmatchedPatient_whenPidNameIsMalformed() {
        when(pids.findByMessageId(7)).thenReturn(List.of(pid("SMITH")));

        new Message("now").patientRouteReport(7);

        ArgumentCaptor<PatientLabRouting> routing = ArgumentCaptor.forClass(PatientLabRouting.class);
        verify(patientRouting).persist(routing.capture());
        assertThat(routing.getValue().getDemographicNo()).isZero();
        verifyNoInteractions(demographics);
    }

    @Test
    void shouldRouteToMatchedPatient_whenPidMatchesDemographic() {
        when(pids.findByMessageId(7)).thenReturn(List.of(pid("SMITH^JANE")));
        Demographic matched = new Demographic();
        matched.setDemographicNo(42);
        when(demographics.findByCriterion(any())).thenReturn(List.of(matched));

        new Message("now").patientRouteReport(7);

        ArgumentCaptor<PatientLabRouting> routing = ArgumentCaptor.forClass(PatientLabRouting.class);
        verify(patientRouting).persist(routing.capture());
        assertThat(routing.getValue().getDemographicNo()).isEqualTo(42);
    }

    @Test
    void shouldPropagateFailure_whenDemographicLookupFails() {
        when(pids.findByMessageId(7)).thenReturn(List.of(pid("SMITH^JANE")));
        when(demographics.findByCriterion(any())).thenThrow(new PersistenceException("database unavailable"));

        // Before, this was stored as unmatched and the batch committed.
        assertThatThrownBy(() -> new Message("now").patientRouteReport(7))
                .isInstanceOf(PersistenceException.class);
        verify(patientRouting, never()).persist(any());
    }
}
