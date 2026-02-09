/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementCPP;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CaseManagementCPPDAO} verifying save, retrieve,
 * null-field conversion, and ordering behavior during the Hibernate migration.
 *
 * <p>These tests validate that HQL positional parameter queries, the
 * null-to-empty-string conversion in {@code saveCPP}, and the
 * {@code order by update_date desc} retrieval all behave correctly
 * after Hibernate version changes.</p>
 *
 * @since 2026-02-09
 * @see CaseManagementCPPDAO
 * @see CaseManagementCPPDAOImpl
 */
@DisplayName("CaseManagementCPPDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("casemgmt")
@Transactional
public class CaseManagementCPPDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("CaseManagementCPPDAO")
    private CaseManagementCPPDAO caseManagementCPPDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Creates a {@link CaseManagementCPP} instance for testing with populated fields.
     *
     * @param demographicNo String the patient demographic number
     * @param providerNo    String the provider number
     * @return CaseManagementCPP a new unsaved entity with default field values
     */
    private CaseManagementCPP createCPP(String demographicNo, String providerNo) {
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setDemographic_no(demographicNo);
        cpp.setProviderNo(providerNo);
        cpp.setSocialHistory("Social history notes");
        cpp.setFamilyHistory("Family history notes");
        cpp.setMedicalHistory("Medical history notes");
        cpp.setOngoingConcerns("Ongoing concerns notes");
        cpp.setReminders("Reminder notes");
        cpp.setPastMedications("Past medications notes");
        cpp.setOtherFileNumber("OFN-001");
        cpp.setOtherSupportSystems("Support systems notes");
        return cpp;
    }

    @Test
    @Tag("create")
    @DisplayName("should save CPP when valid data provided")
    void shouldSaveCPP_whenValidDataProvided() {
        // Given
        CaseManagementCPP cpp = createCPP("20001", "provA");

        // When
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // Then
        assertThat(cpp.getId()).isNotNull();
        CaseManagementCPP loaded = caseManagementCPPDAO.getCPP("20001");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDemographic_no()).isEqualTo("20001");
        assertThat(loaded.getProviderNo()).isEqualTo("provA");
    }

    @Test
    @Tag("read")
    @DisplayName("should return CPP when demographic number matches")
    void shouldReturnCPP_whenDemographicNoMatches() {
        // Given
        CaseManagementCPP cpp = createCPP("20002", "provB");
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // When
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("20002");

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo("20002");
        assertThat(found.getSocialHistory()).isEqualTo("Social history notes");
        assertThat(found.getFamilyHistory()).isEqualTo("Family history notes");
        assertThat(found.getMedicalHistory()).isEqualTo("Medical history notes");
        assertThat(found.getOngoingConcerns()).isEqualTo("Ongoing concerns notes");
        assertThat(found.getReminders()).isEqualTo("Reminder notes");
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when demographic number does not exist")
    void shouldReturnNull_whenDemographicNoDoesNotExist() {
        // When
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("99999");

        // Then
        assertThat(found).isNull();
    }

    @Test
    @Tag("create")
    @DisplayName("should set update date when saving")
    void shouldSetUpdateDate_whenSaving() {
        // Given
        CaseManagementCPP cpp = createCPP("20004", "provD");
        // The entity has no update_date set; saveCPP sets it to new Date()
        Date beforeSave = new Date();

        // When
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();
        Date afterSave = new Date();

        // Then - update_date should be between beforeSave and afterSave
        assertThat(cpp.getUpdate_date()).isNotNull();
        assertThat(cpp.getUpdate_date()).isBetween(beforeSave, afterSave, true, true);
    }

    @Test
    @Tag("create")
    @DisplayName("should convert null fields to empty strings when saving")
    void shouldConvertNullFieldsToEmptyStrings_whenSaving() {
        // Given - create CPP with null values for the fields that saveCPP handles
        CaseManagementCPP cpp = new CaseManagementCPP();
        cpp.setDemographic_no("20005");
        cpp.setProviderNo("provE");
        cpp.setFamilyHistory(null);
        cpp.setMedicalHistory(null);
        cpp.setSocialHistory(null);
        cpp.setOngoingConcerns(null);
        cpp.setReminders(null);
        cpp.setOtherFileNumber(null);
        cpp.setOtherSupportSystems(null);
        cpp.setPastMedications(null);

        // When
        caseManagementCPPDAO.saveCPP(cpp);
        entityManager.flush();

        // Then - all null fields should have been converted to empty strings
        // Verify on the in-memory object (saveCPP modifies the entity directly)
        assertThat(cpp.getFamilyHistory()).isEqualTo("");
        assertThat(cpp.getMedicalHistory()).isEqualTo("");
        assertThat(cpp.getSocialHistory()).isEqualTo("");
        assertThat(cpp.getOngoingConcerns()).isEqualTo("");
        assertThat(cpp.getReminders()).isEqualTo("");
        assertThat(cpp.getOtherFileNumber()).isEqualTo("");
        assertThat(cpp.getOtherSupportSystems()).isEqualTo("");
        assertThat(cpp.getPastMedications()).isEqualTo("");

        // Also verify via a fresh load from the database
        CaseManagementCPP loaded = caseManagementCPPDAO.getCPP("20005");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFamilyHistory()).isEqualTo("");
        assertThat(loaded.getMedicalHistory()).isEqualTo("");
        assertThat(loaded.getSocialHistory()).isEqualTo("");
        assertThat(loaded.getOngoingConcerns()).isEqualTo("");
        assertThat(loaded.getReminders()).isEqualTo("");
        assertThat(loaded.getOtherFileNumber()).isEqualTo("");
        assertThat(loaded.getOtherSupportSystems()).isEqualTo("");
        assertThat(loaded.getPastMedications()).isEqualTo("");
    }

    @Test
    @Tag("read")
    @DisplayName("should return most recent CPP when multiple exist for demographic")
    void shouldReturnMostRecent_whenMultipleCPPsExistForDemographic() {
        // Given - persist two CPPs with different update dates directly
        // via EntityManager to avoid the DAO's automatic update_date override
        CaseManagementCPP olderCpp = new CaseManagementCPP();
        olderCpp.setDemographic_no("20006");
        olderCpp.setProviderNo("provOld");
        olderCpp.setSocialHistory("Older social history");
        olderCpp.setUpdate_date(new Date(System.currentTimeMillis() - 86400000));
        entityManager.persist(olderCpp);

        CaseManagementCPP newerCpp = new CaseManagementCPP();
        newerCpp.setDemographic_no("20006");
        newerCpp.setProviderNo("provNew");
        newerCpp.setSocialHistory("Newer social history");
        newerCpp.setUpdate_date(new Date());
        entityManager.persist(newerCpp);

        entityManager.flush();
        // Clear first-level cache so the DAO query hits the database
        entityManager.clear();

        // When
        CaseManagementCPP found = caseManagementCPPDAO.getCPP("20006");

        // Then - should return the CPP with the most recent update_date
        assertThat(found).isNotNull();
        assertThat(found.getDemographic_no()).isEqualTo("20006");
        assertThat(found.getProviderNo()).isEqualTo("provNew");
        assertThat(found.getSocialHistory()).isEqualTo("Newer social history");
    }
}
