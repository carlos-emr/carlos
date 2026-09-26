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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReportFragmentViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the "unbilled" branch of
 * {@link BillingReportFragmentViewModelAssembler}: the No-Show / Cancelled
 * checkboxes must reach the DAO as include flags, and the default must
 * exclude both statuses (issue #3960).
 *
 * @since 2026-09-26
 */
@DisplayName("BillingReportFragmentViewModelAssembler unbilled status filter")
@Tag("unit")
@Tag("billing")
class BillingReportFragmentUnbilledUnitTest {

    private OscarAppointmentDao appointmentDao;
    private BillingReportFragmentViewModelAssembler assembler;

    @BeforeEach
    void setUp() {
        appointmentDao = mock(OscarAppointmentDao.class);
        assembler = new BillingReportFragmentViewModelAssembler(
                mock(BillingDao.class), mock(BillingDetailDao.class), appointmentDao);
        when(appointmentDao.search_unbill_history_daterange(
                anyString(), any(Date.class), any(Date.class), anyBoolean(), anyBoolean()))
                .thenReturn(List.of());
    }

    private static MockHttpServletRequest unbilledRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("providerview", "999998");
        request.setParameter("xml_vdate", "2026-01-01");
        request.setParameter("xml_appointment_date", "2026-01-31");
        return request;
    }

    @Test
    void shouldExcludeNoShowAndCancelled_whenNoCheckboxSubmitted() {
        assembler.assemble(unbilledRequest(), null, "unbilled");

        verify(appointmentDao).search_unbill_history_daterange(
                eq("999998"), any(Date.class), any(Date.class), eq(false), eq(false));
        verify(appointmentDao, never()).search_unbill_history_daterange(
                anyString(), any(Date.class), any(Date.class));
    }

    @Test
    void shouldPassIncludeFlags_whenCheckboxesSubmitted() {
        MockHttpServletRequest request = unbilledRequest();
        request.setParameter("includeNoShow", "true");
        request.setParameter("includeCancelled", "true");

        assembler.assemble(request, null, "unbilled");

        verify(appointmentDao).search_unbill_history_daterange(
                eq("999998"), any(Date.class), any(Date.class), eq(true), eq(true));
    }

    @Test
    void shouldPassOnlyNoShowFlag_whenOnlyNoShowSubmitted() {
        MockHttpServletRequest request = unbilledRequest();
        request.setParameter("includeNoShow", "true");

        assembler.assemble(request, null, "unbilled");

        verify(appointmentDao).search_unbill_history_daterange(
                eq("999998"), any(Date.class), any(Date.class), eq(true), eq(false));
    }

    @Test
    void shouldRenderRow_forReturnedAppointment() {
        Appointment appt = new Appointment();
        appt.setId(42);
        appt.setDemographicNo(7);
        appt.setName("FAKE-Patient, Test");
        appt.setProviderNo("999998");
        appt.setAppointmentDate(new Date());
        appt.setStartTime(new Date());
        appt.setStatus("N");
        when(appointmentDao.search_unbill_history_daterange(
                anyString(), any(Date.class), any(Date.class), eq(true), eq(false)))
                .thenReturn(List.of(appt));
        MockHttpServletRequest request = unbilledRequest();
        request.setParameter("includeNoShow", "true");

        BillingReportFragmentViewModel model = assembler.assemble(request, null, "unbilled");

        assertThat(model.getUnbilledRows()).hasSize(1);
        assertThat(model.getUnbilledRows().get(0).apptNo()).isEqualTo("42");
    }
}
