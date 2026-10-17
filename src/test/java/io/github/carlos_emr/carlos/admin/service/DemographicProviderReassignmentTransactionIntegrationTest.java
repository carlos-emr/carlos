/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManagerImpl;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
        assertThat(patientArchives.findByDemographicNo(id)).extracting("providerNo").containsExactly("999998");
        assertThat(entityManager.createQuery("select a.value from DemographicExtArchive a where a.demographicNo = :id",String.class)
                .setParameter("id",id).getResultList()).containsExactly("999998");
        entityManager.clear();
        assertThat(patients.getDemographicById(id).getProviderNo()).isEqualTo("999997");
        assertThat(extensions.find(resident.getId()).getValue()).isEqualTo("999997");
    }
}
