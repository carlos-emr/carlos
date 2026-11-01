/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.dao.projection.DemographicName;
import io.github.carlos_emr.carlos.managers.AppointmentManagerImpl;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.ScheduleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real SQL regression and synthetic service-response comparison for issue 5306. */
@Tag("integration")
@Tag("dao")
class DayScheduleNamesDaoIntegrationTest extends CarlosTestBase {
    @Autowired private DemographicDao demographicDao;
    @Autowired private ProviderDao providerDao;
    @Autowired private OscarAppointmentDao appointmentDao;
    @PersistenceContext private EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"1,false", "25,false", "100,false", "1,true", "25,true", "100,true"})
    void shouldUseOneNameQuery_whenScheduleHasDistinctOrRepeatedPatients(int count, boolean repeated) {
        Provider provider = new Provider();
        provider.setProviderNo("530601");
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSpecialty("00");
        provider.setStatus("1");
        provider.setFirstName("Synthetic");
        provider.setLastName("Clinician");
        entityManager.persist(provider);
        List<Appointment> appointments = new ArrayList<>();
        int patientCount = repeated ? Math.min(5, count) : count;
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < patientCount; i++) {
            Demographic patient = new Demographic();
            patient.setSex("M");
            patient.setFirstName("Patient" + i);
            patient.setLastName("Synthetic");
            patient.setProviderNo(provider.getProviderNo());
            entityManager.persist(patient);
            ids.add(patient.getDemographicNo());
        }
        entityManager.flush();
        for (int i = 0; i < count; i++) {
            appointments.add(appointment(i + 1, ids.get(i % patientCount)));
        }
        LoggedInInfo info = new LoggedInInfo();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(info), eq("_demographic"), eq(SecurityInfoManager.READ), anyInt())).thenReturn(true);
        ScheduleService bulk = service(manager(false, security), appointments, info);
        ScheduleService legacy = service(manager(true, security), appointments, info);
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            entityManager.clear();
            statistics.clear();
            var legacyResponse = legacy.getAppointmentsForDay("530601", "2026-10-31");
            long legacyQueries = statistics.getPrepareStatementCount();
            // One demographic and one next-appointment SELECT per appointment,
            // eager merged-record collection reads per distinct patient, and one shared MRP.
            assertThat(legacyQueries).isGreaterThanOrEqualTo(2L * count);

            entityManager.clear();
            statistics.clear();
            var bulkResponse = bulk.getAppointmentsForDay("530601", "2026-10-31");
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getCollectionLoadCount()).isZero();
            assertThat(bulkResponse).usingRecursiveComparison().isEqualTo(legacyResponse);

            if (Boolean.getBoolean("schedule.names.benchmark")) {
                double[] oldTimes = timings(legacy);
                double[] newTimes = timings(bulk);
                System.out.printf(java.util.Locale.ROOT,
                    "SCHEDULE_NAMES appointments=%d repeated=%s legacyQueries=%d bulkQueries=1 legacyMedianMs=%.3f bulkMedianMs=%.3f legacyP95Ms=%.3f bulkP95Ms=%.3f%n",
                    count, repeated, legacyQueries, oldTimes[7], newTimes[7], oldTimes[14], newTimes[14]);
            }
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void shouldPreserveNamesAndDeduplicate_whenInputCrossesBatchBoundary() {
        List<DemographicName> expected = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            Demographic patient = new Demographic();
            patient.setSex("M");
            patient.setFirstName("BoundaryFirst" + i);
            patient.setLastName("BoundaryLast" + i);
            entityManager.persist(patient);
            expected.add(new DemographicName(patient.getDemographicNo(), patient.getLastName(), patient.getFirstName()));
            if (i < 500) ids.add(patient.getDemographicNo());
        }
        entityManager.flush();
        entityManager.clear();
        // 501 inputs but only 500 distinct patients must still fit in one batch.
        ids.add(ids.get(0));
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            assertThat(demographicDao.getDemographicNames(List.of())).isEmpty();
            assertThat(statistics.getPrepareStatementCount()).isZero();

            assertThat(demographicDao.getDemographicNames(ids))
                    .containsExactlyInAnyOrderElementsOf(expected.subList(0, 500));
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getCollectionLoadCount()).isZero();

            statistics.clear();
            ids.add(expected.get(500).demographicNo());
            assertThat(demographicDao.getDemographicNames(ids))
                    .containsExactlyInAnyOrderElementsOf(expected);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            assertThat(statistics.getEntityLoadCount()).isZero();
            assertThat(statistics.getCollectionLoadCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    private DemographicManagerImpl manager(boolean legacy, SecurityInfoManager security) {
        // Adapt the unchanged single-patient method to reproduce the old endpoint's
        // per-appointment work, including repeated IDs and real MRP/next-appointment reads.
        DemographicManagerImpl manager = legacy ? new DemographicManagerImpl() {
            @Override
            public Map<Integer, String> getDemographicFormattedNames(LoggedInInfo info, Collection<Integer> ids) {
                Map<Integer, String> names = new HashMap<>();
                for (Integer id : ids) names.put(id, getDemographicFormattedName(info, id));
                return names;
            }
        } : new DemographicManagerImpl();
        ProviderManager2 providerManager = new ProviderManager2();
        ReflectionTestUtils.setField(providerManager, "providerDao", providerDao);
        AppointmentManagerImpl appointmentManager = new AppointmentManagerImpl();
        ReflectionTestUtils.setField(appointmentManager, "appointmentDao", appointmentDao);
        ReflectionTestUtils.setField(manager, "demographicDao", demographicDao);
        ReflectionTestUtils.setField(manager, "securityInfoManager", security);
        ReflectionTestUtils.setField(manager, "providerManager", providerManager);
        ReflectionTestUtils.setField(manager, "appointmentManager", appointmentManager);
        return manager;
    }

    private ScheduleService service(DemographicManagerImpl manager, List<Appointment> appointments, LoggedInInfo info) {
        ScheduleManager schedule = mock(ScheduleManager.class);
        when(schedule.getDayAppointments(eq(info), anyString(), any(java.util.Date.class))).thenReturn(appointments);
        ScheduleService service = new ScheduleService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return info; }
        };
        ReflectionTestUtils.setField(service, "scheduleManager", schedule);
        ReflectionTestUtils.setField(service, "demographicManager", manager);
        return service;
    }

    private Appointment appointment(int id, int patientId) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setDemographicNo(patientId);
        appointment.setAppointmentDate(java.sql.Date.valueOf("2026-10-31"));
        appointment.setStartTime(Time.valueOf("09:00:00"));
        appointment.setEndTime(Time.valueOf("09:15:00"));
        appointment.setReason("Synthetic follow-up");
        appointment.setStatus("t");
        return appointment;
    }

    private double[] timings(ScheduleService service) {
        double[] samples = new double[15];
        for (int i = -5; i < samples.length; i++) {
            entityManager.clear();
            long start = System.nanoTime();
            service.getAppointmentsForDay("530601", "2026-10-31");
            if (i >= 0) samples[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(samples);
        return samples;
    }
}
