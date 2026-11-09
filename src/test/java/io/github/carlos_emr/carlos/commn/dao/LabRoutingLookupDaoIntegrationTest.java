/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

/** Patient matching SQL semantics on H2 and the physical MariaDB DAO lane. */
@Tag("integration")
@Tag("dao")
class LabRoutingLookupDaoIntegrationTest extends CarlosTestBase {
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @Autowired private LabRoutingLookupDao lookup;

    @BeforeEach
    void syntheticPatients() {
        em.createNativeQuery("INSERT INTO demographic (demographic_no,hin,year_of_birth,month_of_birth,date_of_birth,"
                + "last_name,first_name,sex,provider_no) VALUES (1900554801,'5548000000','2000','01','02','Fixture','Alpha','U','554801'),"
                + "(1900554802,'5548000001','2000','01','02','Other','Beta','F','554802')").executeUpdate();
    }

    @Test
    void shouldPreserveBcLikeMatchingAndSpireShortestNameLimit_forProviderSearch() {
        Provider shortName = new Provider("954601", "Fixture", "doctor", "F", "", "Al");
        shortName.setHsoNo("BC9546A");
        Provider longName = new Provider("954602", "Fixture", "doctor", "F", "", "Alexandra");
        longName.setHsoNo("BC9546B");
        em.persist(shortName);
        em.persist(longName);
        em.flush();
        assertThat(lookup.findProviders("hso_no", "BC9546%", false, null, false))
                .containsExactlyInAnyOrder("954601", "954602");
        assertThat(lookup.findProviders("hso_no", " BC9546%", false, null, false)).isEmpty();
        assertThat(lookup.findProviders("hso_no", "BC9546%", false, 1, true))
                .containsExactly("954601");
        assertThat(lookup.findProviders("hso_no", "BC9546Z", false, 1, true)).isEmpty();
    }

    @Test
    void shouldPreferHinAndDob_andStripOnlyNumericOntarioVersionCodes() {
        assertThat(lookup.findHl7Patients("Different", "Name", "F", "2000-01-02", "5548000000AB", true))
                .containsExactly(new LabRoutingLookupDao.Patient(1900554801, "554801"));
        assertThat(lookup.findHl7Patients("Fixture", "Alpha", "F", "2001-01-02", "5548000000", true)).isEmpty();
        assertThat(lookup.findHl7Patients("Fixture", "Alpha", "F", "2000-01-02", "UNKNOWN00000", true)).isEmpty();
    }

    @Test
    void shouldRequireFullNamesDobAndSex_whenHinIsMissing() {
        assertThat(lookup.findHl7Patients("Fixture", "Alpha", "F", "2000-01-02", "", true))
                .containsExactly(new LabRoutingLookupDao.Patient(1900554801, "554801"));
        assertThat(lookup.findHl7Patients("Fixture", "A", "F", "2000-01-02", "", true)).isEmpty();
        assertThat(lookup.findHl7Patients("Fixture", "Alpha", "UNKNOWN", "2000-01-02", "", true)).isEmpty();
        assertThat(lookup.findHl7Patients("Fixture", "Alpha", "F", "UNKNOWN", "", true)).isEmpty();
    }

    @Test
    void shouldApplyNamePrefixesOnlyWhenConfigured_andBindQuotedNames() {
        assertThat(lookup.findHl7Patients("Foobar", "Another", "F", "2000-01-02", "5548000000", false)).hasSize(1);
        assertThat(lookup.findHl7Patients("Other", "Alpha", "F", "2000-01-02", "5548000000", false)).isEmpty();
        assertThat(lookup.findHl7Patients("Fixture' OR 1=1 --", "Alpha", "F", "2000-01-02", "", true)).isEmpty();
        assertThatThrownBy(() -> lookup.findHl7Patients("Fixture", null, "F", "2000-01-02", "5548000000", false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldKeepCmlGenderRules_separateFromHl7WildcardGender() {
        assertThat(lookup.findCmlPatients("5548000000", "2000", "01", "02", "F", "Fixture", "Alpha", true)).isEmpty();
        assertThat(lookup.findCmlPatients("5548000001", "2000", "01", "02", "F", "Other", "Beta", false))
                .containsExactly(new LabRoutingLookupDao.Patient(1900554802, "554802"));
        assertThat(lookup.findCmlPatients("5548000001", "2000", "01", "02", "F", "Wrong", "Beta", false)).isEmpty();
    }
}
