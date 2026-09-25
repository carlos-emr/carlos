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
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
}
