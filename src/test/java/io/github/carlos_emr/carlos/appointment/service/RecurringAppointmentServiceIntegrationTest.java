/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.appointment.service;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

@Tag("integration")
@Tag("appointment")
@Transactional
class RecurringAppointmentServiceIntegrationTest extends CarlosTestBase {
    @Autowired private OscarAppointmentDao appointments;
    @Autowired private AppointmentArchiveDao archives;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private RecurringAppointmentService service;
    private Map<String, String> values;

    @BeforeEach void setup() {
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(user), eq("_appointment"), anyString(), isNull())).thenReturn(true);
        service = proxied(appointments);
        values = new HashMap<>(Map.of("provider_no", "999998", "appointment_date", "2027-01-31",
                "start_time", "10:00", "end_time", "10:14", "demographic_no", "1", "keyword", "Recurrence fixture",
                "groupappt", "Add Group Appointment", "everyNum", "1", "everyUnit", "week", "endDate", "14/02/2027"));
        values.put("notes", "PW_RECURRENCE_INTEGRATION");
        values.put("status", "t");
        values.put("reasonCode", "-1"); // The stock appointment form's "Other" option.
    }

    private RecurringAppointmentService proxied(OscarAppointmentDao dao) {
        ProxyFactory factory = new ProxyFactory(new RecurringAppointmentService(dao, archives, security));
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (RecurringAppointmentService) factory.getProxy();
    }

    @SuppressWarnings("unchecked")
    private List<Appointment> rows() {
        return em.createQuery("SELECT a FROM Appointment a WHERE a.notes = :marker ORDER BY a.appointmentDate", Appointment.class)
                .setParameter("marker", "PW_RECURRENCE_INTEGRATION").getResultList();
    }

    @Test void createsRepeatsFromSavedAppointmentAndRepeatedSubmitDoesNotDuplicate() {
        values.put("endDate", "31/01/2027");
        assertThat(service.apply(user, values, 10016)).isEqualTo(1);
        Appointment anchor = rows().getFirst();
        values.put("appointment_no", anchor.getId().toString());
        values.put("endDate", "14/02/2027");
        assertThat(service.apply(user, values, 10016)).isEqualTo(2);
        assertThat(service.apply(user, values, 10016)).isZero();
        assertThat(rows()).hasSize(3).allSatisfy(a -> {
            assertThat(a.getCreateDateTime()).isEqualTo(anchor.getCreateDateTime());
            assertThat(a.getCreator()).isEqualTo(anchor.getCreator());
            assertThat(a.getProgramId()).isEqualTo(10016);
        });
        assertThat(rows()).extracting(a -> a.getAppointmentDate().toString())
                .containsExactly("2027-01-31", "2027-02-07", "2027-02-14");
    }

    @Test void updatesCancelsAndArchivesEveryMatchingDate() {
        service.apply(user, values, 10016);
        values.put("appointment_no", rows().getFirst().getId().toString());
        values.put("groupappt", "Group Update");
        values.put("reason", "Changed for the whole series");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).allSatisfy(a -> assertThat(a.getReason()).isEqualTo(values.get("reason")));
        values.put("groupappt", "Group Cancel");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo("C"));
        values.put("groupappt", "Group Delete");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).isEmpty();
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM appointmentArchive WHERE notes='PW_RECURRENCE_INTEGRATION'").getSingleResult();
        assertThat(count.intValue()).isEqualTo(9);
    }

    @Test void preservesUnsubmittedStyleAndBillingWhenCreatingSavedRepeats() {
        values.put("endDate", "31/01/2027");
        values.put("style", "legacy");
        values.put("billing", "BILL");
        service.apply(user, values, 10016);
        values.put("appointment_no", rows().getFirst().getId().toString());
        values.put("endDate", "14/02/2027");
        values.remove("style");
        values.remove("billing");
        assertThat(service.apply(user, values, 10016)).isEqualTo(2);
        em.flush();
        em.clear();
        assertThat(rows()).allSatisfy(a -> {
            assertThat(a.getStyle()).isEqualTo("legacy");
            assertThat(a.getBilling()).isEqualTo("BILL");
        });
    }

    @Test void updatesPreserveEachOccurrencesUnsubmittedMetadataButAllowExplicitClearing() {
        service.apply(user, values, 10016);
        List<Appointment> series = rows();
        for (int i = 0; i < series.size(); i++) {
            series.get(i).setStyle("style-" + i);
            series.get(i).setBilling("billing-" + i);
        }
        em.flush();
        values.put("appointment_no", series.getFirst().getId().toString());
        values.put("groupappt", "Group Update");
        values.put("reason", "updated reason");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        em.flush();
        em.clear();
        series = rows();
        for (int i = 0; i < series.size(); i++) {
            assertThat(series.get(i).getStyle()).isEqualTo("style-" + i);
            assertThat(series.get(i).getBilling()).isEqualTo("billing-" + i);
            assertThat(series.get(i).getReason()).isEqualTo("updated reason");
        }
        values.put("style", "");
        values.put("billing", "");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        em.flush();
        em.clear();
        assertThat(rows()).allSatisfy(a -> {
            assertThat(a.getStyle()).isEmpty();
            assertThat(a.getBilling()).isEmpty();
        });
    }

    @Test void doesNotIgnoreUnsavedChangesOrInvalidEndDate() {
        service.apply(user, values, 10016);
        values.put("appointment_no", rows().getFirst().getId().toString());
        values.put("reason", "Unsaved change");
        assertThatIllegalArgumentException().isThrownBy(() -> service.apply(user, values, 10016))
                .withMessageContaining("Save changes");
        assertThat(rows()).hasSize(3).allSatisfy(a -> assertThat(a.getReason()).isEmpty());
    }

    @Test void invalidRangeWritesNothing() {
        values.put("endDate", "30/01/2027");
        assertThatIllegalArgumentException().isThrownBy(() -> service.apply(user, values, 10016))
                .withMessageContaining("end date");
        assertThat(rows()).isEmpty();
    }

    @Test void preservesNonPatientBookingsAndTextProviderIdentifiers() {
        values.put("demographic_no", "");
        values.put("provider_no", "LOCUM");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).allSatisfy(a -> {
            assertThat(a.getDemographicNo()).isZero();
            assertThat(a.getProviderNo()).isEqualTo("LOCUM");
        });
    }

    @Test void managementBoundsActualMembersInsteadOfAnUnusedDailyPattern() {
        service.apply(user, values, 10016);
        values.put("appointment_no", rows().getFirst().getId().toString());
        values.put("everyUnit", "day");
        values.put("endDate", "31/01/2030");
        values.put("groupappt", "Group Cancel");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo("C"));
    }

    @Test void managesLegacyMonthEndSeriesWithoutMissingDriftedDates() {
        values.put("everyUnit", "month");
        values.put("endDate", "31/03/2027");
        service.apply(user, values, 10016);
        List<Appointment> series = rows();
        series.get(2).setAppointmentDate(Date.valueOf("2027-03-28"));
        appointments.merge(series.get(2));
        em.flush();
        values.put("appointment_no", series.getFirst().getId().toString());
        values.put("groupappt", "Group Cancel");
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
        assertThat(rows()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo("C"));
    }

    @Test void preservesWriteOnlyAccessForNewRecurringBookings() {
        when(security.hasPrivilege(eq(user), eq("_appointment"), eq("u"), isNull())).thenReturn(false);
        assertThat(service.apply(user, values, 10016)).isEqualTo(3);
    }

    @Test void requiresAppointmentPrivileges() {
        values.put("groupappt", "Group Update");
        when(security.hasPrivilege(eq(user), eq("_appointment"), eq("u"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> service.apply(user, values, 10016)).isInstanceOf(SecurityException.class);
        assertThat(rows()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureRollsBackEarlierOccurrences() {
        OscarAppointmentDao failing = mock(OscarAppointmentDao.class, delegatesTo(appointments));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("simulated second insert failure");
            appointments.persist(invocation.getArgument(0));
            return null;
        }).when(failing).persist(any(Appointment.class));
        assertThatThrownBy(() -> proxied(failing).apply(user, values, 10016))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("second insert");
        new TransactionTemplate(transactions).executeWithoutResult(status -> assertThat(rows()).isEmpty());
    }
}
