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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportField;
import io.github.carlos_emr.carlos.commn.model.CdsImportPatient;
import io.github.carlos_emr.carlos.commn.model.CdsImportReview;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The lookups behind every "CDS source fields" link, run for real: the two queries added for the
 * presence decision and the review-provenance query it reuses, through the actual DAOs and JPA
 * mappings, on an owned in-memory database.
 *
 * <p>The CDS ledger tables are not part of the shared application test schema, so this test owns a
 * uniquely named H2 database built from the three entity mappings and shuts it down afterwards.
 * The physical MariaDB behaviour of the ledger is qualified separately by the
 * {@code *MariaDbIntegrationTest} tier; the access policy is mocked here and has its own tests.</p>
 */
@Tag("integration")
@Tag("dao")
@Tag("read")
@DisplayName("CDS source link presence against a real database")
class CdsSourceFieldPresenceIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config { }

    private static final String RUN = "00000000-0000-4000-8000-0000000000aa";
    private static final int IMPORTED = 4101;
    private static final int REVIEWED_ONLY = 4102;
    private static final int UNTOUCHED = 4103;

    private static AnnotationConfigApplicationContext context;
    private static CdsImportFieldDao fields;
    private static CdsImportPatientDao patients;
    private static CdsImportReviewDao reviews;
    private static TransactionTemplate transaction;

    private CdsSourceFieldPresenceResolver resolver;
    private MockHttpServletRequest request;

    @BeforeAll
    static void openDatabase() {
        String url = "jdbc:h2:mem:cds_presence_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DriverManagerDataSource.class, () -> new DriverManagerDataSource(url, "sa", ""));
        context.registerBean("entityManagerFactory", LocalContainerEntityManagerFactoryBean.class, () -> {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setPersistenceUnitName("entityManagerFactory");
            factory.setDataSource(context.getBean(DataSource.class));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    CdsImportField.class.getName(), CdsImportPatient.class.getName(), CdsImportReview.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.dialect", "org.hibernate.dialect.H2Dialect", "hibernate.show_sql", "false"));
            return factory;
        });
        context.registerBean("transactionManager", PlatformTransactionManager.class,
                () -> new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
        context.registerBean("persistenceAnnotationBeanPostProcessor", PersistenceAnnotationBeanPostProcessor.class);
        context.register(CdsImportFieldDao.class, CdsImportPatientDao.class, CdsImportReviewDao.class);
        context.refresh();
        fields = context.getBean(CdsImportFieldDao.class);
        patients = context.getBean(CdsImportPatientDao.class);
        reviews = context.getBean(CdsImportReviewDao.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void closeDatabase() {
        if (context == null) return;
        DataSource dataSource = context.getBean(DataSource.class);
        // Closing the factory drops the schema; DB_CLOSE_DELAY=-1 then needs an explicit shutdown.
        context.close();
        new JdbcTemplate(dataSource).execute("SHUTDOWN");
    }

    @BeforeEach
    void seedOwnedRows() {
        write(em -> {
            em.createQuery("delete from CdsImportField").executeUpdate();
            em.createQuery("delete from CdsImportReview").executeUpdate();
            em.createQuery("delete from CdsImportPatient").executeUpdate();
            // An imported chart with preserved fields in two categories (two fields in one of them).
            em.persist(patient("patient-a", "source-a", IMPORTED));
            em.persist(field("a".repeat(64), IMPORTED, "ClinicalNotes"));
            em.persist(field("b".repeat(64), IMPORTED, "ClinicalNotes"));
            em.persist(field("c".repeat(64), IMPORTED, "Demographics"));
            // A chart an import matched and an operator then declined: review provenance, no fields.
            em.persist(patient("patient-b", "source-b", REVIEWED_ONLY));
            em.persist(review("11111111-1111-4111-8111-111111111111", "source-b", "DECLINE_PATIENT", "DECLINE_READY", null));
            // A review that is not yet in a listed state must not count.
            em.persist(review("22222222-2222-4222-8222-222222222222", "source-a", "CORRECT_VALUE", "REQUESTED", "NewCategory"));
            // An import patient row that never reached a chart.
            em.persist(patient("patient-c", "source-c", null));
        });
        // The viewer may read everything: an unstubbed requireClinicalRead returns normally.
        resolver = new CdsSourceFieldPresenceResolver(fields, patients, reviews, mock(CdsImportAccessService.class));
        request = new MockHttpServletRequest();
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", new LoggedInInfo());
    }

    @Test
    @DisplayName("should list each category once for a patient with preserved source fields")
    void shouldListDistinctCategories_forPatientWithSourceFields() {
        assertThat(fields.categories(IMPORTED)).containsExactlyInAnyOrder("ClinicalNotes", "Demographics");
        assertThat(fields.categories(REVIEWED_ONLY)).isEmpty();
        assertThat(fields.categories(UNTOUCHED)).isEmpty();
    }

    @Test
    @DisplayName("should recognize a chart as an import target only when an import patient row names it")
    void shouldRecognizeImportTarget_whenPatientRowNamesChart() {
        assertThat(patients.target(IMPORTED)).isTrue();
        assertThat(patients.target(REVIEWED_ONLY)).isTrue();
        assertThat(patients.target(UNTOUCHED)).isFalse();
    }

    @Test
    @DisplayName("should show no link for a patient no import touched")
    void shouldShowNoLink_forPatientNoImportTouched() {
        for (String category : CdsClinicalSourceService.CATEGORIES) {
            assertThat(resolver.hasSourceFields(request, Integer.toString(UNTOUCHED), category)).as(category).isFalse();
        }
    }

    @Test
    @DisplayName("should show links only for the categories that hold source fields")
    void shouldShowLinks_forCategoriesWithSourceFields() {
        String patient = Integer.toString(IMPORTED);

        assertThat(resolver.hasSourceFields(request, patient, "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, patient, "Demographics")).isTrue();
        // No field there, and its only review is not in a state the source view lists.
        assertThat(resolver.hasSourceFields(request, patient, "NewCategory")).isFalse();
        assertThat(resolver.hasSourceFields(request, patient, "LaboratoryResults")).isFalse();
    }

    @Test
    @DisplayName("should show a link where a matched chart has only review provenance")
    void shouldShowLink_whereMatchedChartHasOnlyReviewProvenance() {
        String patient = Integer.toString(REVIEWED_ONLY);

        // A patient-level decline is listed under every category of the source view.
        assertThat(reviews.provenance(REVIEWED_ONLY, "ClinicalNotes", "", 1)).hasSize(1);
        assertThat(resolver.hasSourceFields(request, patient, "ClinicalNotes")).isTrue();
        assertThat(resolver.hasSourceFields(request, patient, "Appointments")).isTrue();
    }

    private static void write(Consumer<EntityManager> action) {
        transaction.executeWithoutResult(status -> action.accept(
                EntityManagerFactoryUtils.getTransactionalEntityManager(context.getBean(EntityManagerFactory.class))));
    }

    private static CdsImportField field(String id, int demographicNo, String category) {
        CdsImportField field = new CdsImportField();
        field.setId(id);
        field.setRunId(RUN);
        field.setPatientKey("1".repeat(64));
        field.setUnitKey(id);
        field.setDemographicNo(demographicNo);
        field.setCategory(category);
        field.setSourcePath("/OmdCds/PatientRecord/" + category + "[1]");
        field.setOccurrence(1);
        field.setValueType("TEXT");
        field.setOriginalValue("synthetic source value");
        field.setNormalizedValue("synthetic source value");
        field.setPayloadOffset(0);
        field.setPayloadLength(0);
        field.setSourceNamespace("cds");
        return field;
    }

    private static CdsImportPatient patient(String key, String sourcePatientId, Integer targetDemographicNo) {
        CdsImportPatient patient = new CdsImportPatient();
        patient.setId(key);
        patient.setRunId(RUN);
        patient.setPatientKey(key);
        patient.setSourcePatientId(sourcePatientId);
        patient.setCopyKey("copy-" + key);
        patient.setLineageKey("lineage-" + key);
        patient.setSourceDigest("d".repeat(64));
        patient.setTargetDemographicNo(targetDemographicNo);
        patient.setState("COMPLETE");
        patient.setHeld(false);
        patient.setExpectedUnits(1);
        return patient;
    }

    private static CdsImportReview review(String id, String sourcePatientId, String kind, String state, String category) {
        CdsImportReview review = new CdsImportReview();
        review.setId(id);
        review.setRunId(RUN);
        review.setReviewVersion(1);
        review.setKind(kind);
        review.setDiagnosticId(1);
        review.setSourcePatientId(sourcePatientId);
        review.setCategory(category);
        review.setAppliedAt(Instant.parse("2026-01-02T03:04:05Z"));
        review.setReviewReason("synthetic review");
        review.setState(state);
        review.setOperator("web-1");
        review.setProviderNo("999998");
        review.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z"));
        review.setPayload("{}");
        review.setPayloadSha256("e".repeat(64));
        review.setPlanLocation("/opaque/plan");
        review.setPlanSha256("f".repeat(64));
        review.setPlanBytes(2);
        return review;
    }
}
