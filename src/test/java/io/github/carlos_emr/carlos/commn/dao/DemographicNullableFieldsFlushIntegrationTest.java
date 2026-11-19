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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Commits real reads of legacy SQL NULLs without changing stored patient data. Public setters
 * and new-patient inserts retain their historical empty-string write behaviour.
 *
 * @since 2026-10-26
 */
@Tag("integration") @Tag("dao") @Tag("demographic")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemographicNullableFieldsFlushIntegrationTest extends CarlosTestBase {
    private static final String COLUMNS = "phone,phone2,chart_no,family_doctor,alias,pronoun,gender,patient_status,roster_status";
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private DemographicDao demographicDao;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    private final List<Fixture> fixtures = new ArrayList<>();

    private record Fixture(Integer patient, Integer program) { }

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }

    private Demographic newPatient() {
        var patient = new Demographic();
        patient.setFirstName("FAKE-Nullable"); patient.setLastName("FAKE-ReadCommit");
        patient.setProviderNo("999998"); patient.setHcType("ON"); patient.setSex("F");
        patient.setYearOfBirth("1980"); patient.setMonthOfBirth("01"); patient.setDateOfBirth("02");
        patient.setPrefName("");
        return patient;
    }

    private Fixture createLegacyPatient(boolean admission, String familyDoctor) {
        Fixture fixture = transaction().execute(status -> {
            Demographic patient = newPatient(); entityManager.persist(patient); entityManager.flush();
            Integer programId = null;
            if (admission) {
                var program = new Program(); program.setName("FAKE-Nullable-Admission"); program.setType("service");
                program.setProgramStatus("active"); program.setMaxAllowed(10); entityManager.persist(program); entityManager.flush();
                programId = program.getId();
                var row = new Admission(); row.setClientId(patient.getDemographicNo()); row.setProgramId(programId);
                row.setProviderNo("999998"); row.setAdmissionStatus(Admission.STATUS_CURRENT); row.setAdmissionDate(new Date());
                entityManager.persist(row); entityManager.flush();
            }
            // Bypass public setters: these are legitimate pre-existing database values, not a write request.
            entityManager.createNativeQuery("UPDATE demographic SET phone=NULL,phone2=NULL,chart_no=NULL,"
                    + "family_doctor=:doctor,alias=NULL,pronoun=NULL,gender=NULL,patient_status=NULL,roster_status=NULL,"
                    + "province=' on ',year_of_birth='',month_of_birth='',date_of_birth='' "
                    + "WHERE demographic_no=:id").setParameter("doctor", familyDoctor)
                    .setParameter("id", patient.getDemographicNo()).executeUpdate();
            entityManager.clear();
            return new Fixture(patient.getDemographicNo(), programId);
        });
        fixtures.add(fixture);
        return fixture;
    }

    private List<?> storedRow(Integer patient) {
        return transaction().execute(status -> entityManager.createNativeQuery(
                "SELECT * FROM demographic WHERE demographic_no=:id").setParameter("id", patient).getResultList());
    }

    private Object[] storedColumns(Integer patient) {
        return transaction().execute(status -> (Object[]) entityManager.createNativeQuery(
                "SELECT " + COLUMNS + " FROM demographic WHERE demographic_no=:id")
                .setParameter("id", patient).getSingleResult());
    }

    private void assertFriendlyValues(Demographic patient, String expected) {
        assertThat(List.of(patient.getPhone(), patient.getPhone2(), patient.getChartNo(), patient.getFamilyDoctor(),
                patient.getAlias(), patient.getPronoun(), patient.getGender(), patient.getPatientStatus(), patient.getRosterStatus()))
                .containsExactly(expected, expected, expected, expected, expected, expected, expected, expected, expected);
        assertThat(patient.getProvince()).isEqualTo("ON");
        assertThat(patient.getYearOfBirth()).isNull();
        assertThat(patient.getMonthOfBirth()).isNull();
        assertThat(patient.getDateOfBirth()).isNull();
        assertThat(patient.getBirthDay()).isNull();
    }

    private void setValues(Demographic patient, String value) {
        patient.setPhone(value); patient.setPhone2(value); patient.setChartNo(value); patient.setFamilyDoctor(value);
        patient.setAlias(value); patient.setPronoun(value); patient.setGender(value);
        patient.setPatientStatus(value); patient.setRosterStatus(value);
    }

    private void assertWrittenValues(Integer id, String input) {
        String expected = input == null ? "" : input;
        String doctor = input == null || input.isBlank() ? "" : input;
        assertThat(storedColumns(id)).containsExactly(expected, expected, expected, doctor, expected,
                expected, expected, expected, expected);
    }

    @AfterEach
    void removeCommittedFixtures() {
        transaction().executeWithoutResult(status -> {
            for (Fixture fixture : fixtures) {
                entityManager.createNativeQuery("DELETE FROM admission WHERE client_id=:id").setParameter("id", fixture.patient()).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM demographic WHERE demographic_no=:id").setParameter("id", fixture.patient()).executeUpdate();
                if (fixture.program() != null) entityManager.createNativeQuery("DELETE FROM program WHERE id=:id")
                        .setParameter("id", fixture.program()).executeUpdate();
            }
        });
    }

    @Test
    void shouldPreserveSqlNulls_whenFriendlyGettersAreReadAndTheTransactionCommits() {
        Fixture fixture = createLegacyPatient(false, null);
        List<?> before = storedRow(fixture.patient());
        boolean dirty = transaction().execute(status -> {
            Demographic patient = demographicDao.getDemographicById(fixture.patient());
            assertFriendlyValues(patient, "");
            assertThat(patient.getFamilyDoctorFirstName()).isEmpty();
            assertThat(patient.getFamilyDoctorLastName()).isEmpty();
            assertThat(patient.getFamilyDoctorNumber()).isEmpty();
            return entityManager.unwrap(Session.class).isDirty();
        });
        assertThat(storedRow(fixture.patient())).usingRecursiveComparison().isEqualTo(before);
        assertThat(dirty).as("display getters must not dirty a patient").isFalse();
    }

    @Test
    void shouldPreserveSqlNulls_whenAdmissionEagerlyLoadsItsPatientAndTheTransactionCommits() {
        Fixture fixture = createLegacyPatient(true, null);
        List<?> before = storedRow(fixture.patient());
        transaction().executeWithoutResult(status -> {
            var dao = new AdmissionDaoImpl(); ReflectionTestUtils.setField(dao, "entityManager", entityManager);
            List<Admission> admissions = dao.getAdmissions(fixture.patient());
            assertThat(admissions).hasSize(1);
            Demographic patient = admissions.getFirst().getClient();
            assertThat(Hibernate.isInitialized(patient)).isTrue();
            assertThat(patient.getDemographicNo()).isEqualTo(fixture.patient());
            assertFriendlyValues(patient, "");
        });
        assertThat(storedRow(fixture.patient())).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void shouldPreserveOtherSqlNulls_whenAnUnrelatedPatientFieldIsWritten() {
        Fixture fixture = createLegacyPatient(false, null);
        transaction().executeWithoutResult(status -> {
            Demographic patient = demographicDao.getDemographicById(fixture.patient());
            assertFriendlyValues(patient, ""); patient.setFirstName("FAKE-Changed");
        });
        assertThat(storedColumns(fixture.patient())).containsOnlyNulls();
        Object[] legacyValues = transaction().execute(status -> (Object[]) entityManager.createNativeQuery(
                "SELECT province,year_of_birth,month_of_birth,date_of_birth FROM demographic WHERE demographic_no=:id")
                .setParameter("id", fixture.patient()).getSingleResult());
        assertThat(legacyValues).containsExactly(" on ", "", "", "");
        Object firstName = transaction().execute(status -> entityManager.createNativeQuery(
                "SELECT first_name FROM demographic WHERE demographic_no=:id")
                .setParameter("id", fixture.patient()).getSingleResult());
        assertThat(firstName).isEqualTo("FAKE-Changed");
    }

    @Test
    void shouldPreserveStoredDoctorWhitespace_whenItsDisplayAndParsedNamesAreRead() {
        Fixture fixture = createLegacyPatient(false, " \t ");
        List<?> before = storedRow(fixture.patient());
        transaction().executeWithoutResult(status -> {
            Demographic patient = demographicDao.getDemographicById(fixture.patient());
            assertThat(patient.getFamilyDoctor()).isEmpty();
            assertThat(patient.getFamilyDoctorFirstName()).isEmpty();
            assertThat(patient.getFamilyDoctorLastName()).isEmpty();
        });
        assertThat(storedRow(fixture.patient())).usingRecursiveComparison().isEqualTo(before);
    }

    @Test
    void shouldKeepHistoricalEmptyDefaults_whenANewPatientIsInserted() {
        Integer id = transaction().execute(status -> {
            Demographic patient = newPatient(); entityManager.persist(patient); entityManager.flush();
            return patient.getDemographicNo();
        });
        fixtures.add(new Fixture(id, null));
        assertWrittenValues(id, null);
    }

    @ParameterizedTest @NullSource @ValueSource(strings={" \t "})
    void shouldPreserveLegacyRawValues_whenAnUnchangedDetachedPatientIsMerged(String doctor) {
        Fixture fixture = createLegacyPatient(false, doctor);
        List<?> before = storedRow(fixture.patient());
        Demographic detached = transaction().execute(status -> demographicDao.getDemographicById(fixture.patient()));
        transaction().executeWithoutResult(status -> entityManager.merge(detached));
        assertThat(storedRow(fixture.patient())).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"FAKE-Write"," \t "})
    void shouldKeepExplicitSetterWriteBehaviour_whenANewPatientIsInserted(String value) {
        Integer id = transaction().execute(status -> {
            Demographic patient = newPatient(); setValues(patient, value); entityManager.persist(patient); entityManager.flush();
            return patient.getDemographicNo();
        });
        fixtures.add(new Fixture(id, null));
        assertWrittenValues(id, value);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"FAKE-Write"," \t "})
    void shouldKeepExplicitSetterWriteBehaviour_whenAnExistingPatientIsChanged(String value) {
        Fixture fixture = createLegacyPatient(false, null);
        transaction().executeWithoutResult(status -> setValues(demographicDao.getDemographicById(fixture.patient()), value));
        assertWrittenValues(fixture.patient(), value);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"FAKE-Write"," \t "})
    void shouldKeepExplicitSetterWriteBehaviour_whenADetachedPatientIsMerged(String value) {
        Fixture fixture = createLegacyPatient(false, null);
        Demographic detached = transaction().execute(status -> demographicDao.getDemographicById(fixture.patient()));
        setValues(detached, value);
        transaction().executeWithoutResult(status -> entityManager.merge(detached));
        assertWrittenValues(fixture.patient(), value);
    }
}
