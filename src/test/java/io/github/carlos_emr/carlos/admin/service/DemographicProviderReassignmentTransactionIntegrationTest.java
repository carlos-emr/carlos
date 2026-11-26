/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.reports.PreventionReportUtil;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real managed entities and archival DAOs inside the surrounding reassignment transaction. */
@Tag("integration") @Tag("demographic")
class DemographicProviderReassignmentTransactionIntegrationTest extends CarlosTestBase {
    @Autowired DemographicDao patients;
    @Autowired DemographicExtDao extensions;
    @Autowired DemographicArchiveDao patientArchives;
    @Autowired DemographicExtArchiveDao extensionArchives;
    @PersistenceContext(unitName="entityManagerFactory") EntityManager entityManager;

    @Test void shouldArchiveOriginalValues_whenReassigningManagedPatientAndResident() throws Exception {
        Demographic patient = new Demographic();
        io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator.generateTestDataForModelClass(patient);
        patient.setFirstName("FAKE");patient.setLastName("FAKE-REASSIGN");
        patient.setYearOfBirth("1980");patient.setMonthOfBirth("01");patient.setDateOfBirth("02");
        // The stored preferred name and the alias are independent columns.
        patient.setPrefName("FAKE-PREF");
        patient.setAlias("FAKE-ALIAS");
        Date previousUpdate = Date.from(Instant.parse("2023-01-01T00:00:00Z"));
        Date earlierReportDate = Date.from(Instant.parse("2024-06-15T00:00:00Z"));
        patient.setLastUpdateDate(previousUpdate);
        patient.setRosterDate(Date.from(Instant.parse("2022-01-01T00:00:00Z")));
        patient.setRosterTerminationDate(null);
        patient.setProviderNo("999998");entityManager.persist(patient);entityManager.flush();
        int id = patient.getDemographicNo();
        DemographicExt resident = new DemographicExt("999998",id,"resident","999998");
        resident.setDateCreated(new Date());extensions.persist(resident);entityManager.flush();
        var user=mock(LoggedInInfo.class);when(user.getLoggedInProviderNo()).thenReturn("999998");
        var security=mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user),anyString(),anyString(),any())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user,id)).thenReturn(true);
        var manager=spy(new DemographicManagerImpl());
        for (var entry : java.util.Map.of("demographicDao",patients,"demographicExtDao",extensions,
                "demographicArchiveDao",patientArchives,"demographicExtArchiveDao",extensionArchives,
                "securityInfoManager",security).entrySet()) {
            ReflectionTestUtils.setField(manager,entry.getKey(),entry.getValue());
        }
        doReturn(List.of(patient)).when(manager).getDemographicsNameRangeByProvider(eq(user),any(),eq("^[A-Z]"));
        doReturn(List.of(id)).when(manager).getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(user,"999998","^[A-Z]");
        doReturn(List.of(resident)).when(manager).getMultipleResidentForDemographicNumbersByProviderNumber(user,List.of(id),"999998");
        var providers=mock(ProviderManager2.class);var old=new Provider();old.setProviderNo("999998");
        when(providers.getProvider(user,"999998")).thenReturn(old);
        var replacement=new Provider();replacement.setProviderNo("999997");when(providers.getProvider(user,"999997")).thenReturn(replacement);
        var service=new DemographicProviderReassignmentService(manager,providers,security);
        assertThat(service.reassign(user,"UpdateMrp","999998","999997","A","Z")).isEqualTo(1);
        assertThat(service.reassign(user,"UpdateResident","999998","999997","A","Z")).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        Demographic saved = patients.getDemographicById(id);
        assertThat(patientArchives.findByDemographicNo(id)).singleElement().satisfies(archive -> {
            assertThat(archive.getProviderNo()).isEqualTo("999998");
            assertThat(archive.getLastUpdateDate().getTime()).isEqualTo(previousUpdate.getTime());
            assertThat(archive.getRosterDate()).isEqualTo(saved.getRosterDate());
            assertThat(archive.getRosterTerminationDate()).isNull();
        });
        assertThat(entityManager.createQuery("select a.value from DemographicExtArchive a where a.demographicNo = :id",String.class)
                .setParameter("id",id).getResultList()).containsExactly("999998");
        assertThat(saved.getProviderNo()).isEqualTo("999997");
        assertThat(entityManager.createNativeQuery("select pref_name from demographic where demographic_no = ?1")
                .setParameter(1, id).getSingleResult()).as("the stored preferred name").isEqualTo("FAKE-PREF");
        assertThat(entityManager.createNativeQuery("select alias from demographic where demographic_no = ?1")
                .setParameter(1, id).getSingleResult()).as("the stored alias").isEqualTo("FAKE-ALIAS");
        assertThat(saved.getLastUpdateDate()).isAfter(earlierReportDate);
        assertThat(saved.getRosterTerminationDate()).isNull();
        assertThat(extensions.find(resident.getId()).getValue()).isEqualTo("999997");

        // Report authorization is outside this fixture. Both versions came from the database.
        doReturn(saved).when(manager).getDemographic(user, id);
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(manager);
            spring.when(() -> SpringUtils.getBean(DemographicArchiveDao.class)).thenReturn(patientArchives);
            assertThat(PreventionReportUtil.wasRosteredToThisProvider(user, id, earlierReportDate, "999998")).isTrue();
            assertThat(PreventionReportUtil.wasRosteredToThisProvider(user, id, earlierReportDate, "999997")).isFalse();
            Date reassignment = saved.getLastUpdateDate();
            assertThat(PreventionReportUtil.wasRosteredToThisProvider(user, id, reassignment, "999997")).isTrue();
            assertThat(PreventionReportUtil.wasRosteredToThisProvider(user, id, reassignment, "999998")).isFalse();
        }
    }
}
