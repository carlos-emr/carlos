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

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ConsultDocsDao} covering consultation
 * document attachment CRUD and filtering by request/docType.
 *
 * <p>Migrated from legacy {@code ConsultDocsDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ConsultDocsDao
 */
@DisplayName("ConsultDocsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("consultation")
@Transactional
public class ConsultDocsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ConsultDocsDao consultDocsDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final String INSERT_DEMOGRAPHIC_SQL = "INSERT INTO demographic (demographic_no, first_name, last_name, sex, provider_no, patient_status) VALUES (:id, 'Test', 'Patient', 'M', :providerNo, 'AC')";

    private ConsultDocs createConsultDoc(int requestId, int documentNo, String docType, String deleted) {
        ConsultDocs doc = new ConsultDocs();
        doc.setRequestId(requestId);
        doc.setDocumentNo(documentNo);
        doc.setDocType(docType);
        doc.setDeleted(deleted);
        consultDocsDao.persist(doc);
        entityManager.flush();
        return doc;
    }

    private void createDemographic(int demographicNo) {
        entityManager.createNativeQuery(INSERT_DEMOGRAPHIC_SQL)
                .setParameter("id", demographicNo)
                .setParameter("providerNo", PROVIDER_NO)
                .executeUpdate();
        entityManager.flush();
    }

    private ConsultationRequest createConsultationRequest(int demographicNo) {
        ConsultationRequest consult = new ConsultationRequest();
        consult.setDemographicId(demographicNo);
        consult.setProviderNo(PROVIDER_NO);
        consult.setReferralDate(new Date());
        consult.setServiceId(1);
        consult.setStatus("1");
        entityManager.persist(consult);
        entityManager.flush();
        return consult;
    }

    private EFormData createEFormData(int demographicNo, boolean patientIndependent) {
        Date now = new Date();
        EFormData eFormData = new EFormData();
        eFormData.setDemographicId(demographicNo);
        eFormData.setFormId(1);
        eFormData.setFormName("TestForm");
        eFormData.setSubject("Test Subject");
        eFormData.setCurrent(true);
        eFormData.setFormDate(now);
        eFormData.setFormTime(now);
        eFormData.setProviderNo(PROVIDER_NO);
        eFormData.setFormData("<form>test</form>");
        eFormData.setShowLatestFormOnly(false);
        eFormData.setPatientIndependent(patientIndependent);
        eFormData.setRoleType("");
        entityManager.persist(eFormData);
        entityManager.flush();
        return eFormData;
    }

    private Document createDocument(char status) {
        Date now = new Date();
        Document document = new Document();
        document.setDoctype("consult");
        document.setDocdesc("Test document");
        document.setDocfilename("test.pdf");
        document.setDoccreator(PROVIDER_NO);
        document.setResponsible(PROVIDER_NO);
        document.setStatus(status);
        document.setContenttype("application/pdf");
        document.setPublic1(0);
        document.setNumberofpages(1);
        document.setObservationdate(now);
        document.setUpdatedatetime(now);
        document.setContentdatetime(now);
        entityManager.persist(document);
        entityManager.flush();
        return document;
    }

    private void createCtlDocument(int demographicNo, Integer documentNo) {
        createCtlDocument(demographicNo, documentNo, String.valueOf(Document.STATUS_ACTIVE));
    }

    private void createCtlDocument(int demographicNo, Integer documentNo, String status) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK("demographic", demographicNo, documentNo));
        ctlDocument.setStatus(status);
        entityManager.persist(ctlDocument);
        entityManager.flush();
    }

    private String deletedValue(ConsultDocs consultDocs) {
        return entityManager.find(ConsultDocs.class, consultDocs.getId()).getDeleted();
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist consult doc with generated ID")
        void shouldPersistConsultDoc_whenValidDataProvided() {
            ConsultDocs doc = createConsultDoc(1001, 2001, "D", null);
            assertThat(doc.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find consult doc by ID")
        void shouldFindConsultDoc_whenValidIdProvided() {
            ConsultDocs saved = createConsultDoc(1002, 2002, "L", null);
            ConsultDocs found = consultDocsDao.find(saved.getId());
            assertThat(found).isNotNull();
            assertThat(found.getRequestId()).isEqualTo(1002);
            assertThat(found.getDocType()).isEqualTo("L");
        }
    }

    @Nested
    @DisplayName("Query operations")
    class QueryOperations {

        @BeforeEach
        void setUp() {
            createConsultDoc(3001, 4001, "D", null);
            createConsultDoc(3001, 4002, "D", null);
            createConsultDoc(3001, 4003, "L", null);
            createConsultDoc(3001, 4004, "D", "Y");
            createConsultDoc(3002, 4005, "D", null);
        }

        @Test
        @Tag("query")
        @DisplayName("should find docs by request ID, doc no, and doc type excluding deleted")
        void shouldFindDocs_byRequestIdDocNoDocType() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocNoDocType(3001, 4001, "D");
            assertThat(results).hasSize(1);
        }

        @Test
        @Tag("query")
        @DisplayName("should find docs by request ID and doc type")
        void shouldFindDocs_byRequestIdAndDocType() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocType(3001, "D");
            assertThat(results).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should find all docs by request ID")
        void shouldFindDocs_byRequestId() {
            List<ConsultDocs> results = consultDocsDao.findByRequestId(3001);
            assertThat(results).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @Tag("query")
        @DisplayName("should exclude deleted docs from results")
        void shouldExcludeDeletedDocs_fromResults() {
            List<ConsultDocs> results = consultDocsDao.findByRequestIdDocNoDocType(3001, 4004, "D");
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list for non-existent request")
        void shouldReturnEmptyList_whenRequestNotFound() {
            List<ConsultDocs> results = consultDocsDao.findByRequestId(99999);
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("stale active consult attachment cleanup")
    class StaleActiveConsultAttachmentCleanup {

        @Test
        @DisplayName("should report only invalid active eForm and document attachments")
        void shouldReportOnlyInvalidActiveEFormAndDocumentAttachments_forCleanupDryRun() {
            CleanupFixture fixture = createCleanupFixture();

            List<ConsultDocs> results = consultDocsDao.findStaleActiveConsultAttachments();

            assertThat(results)
                    .extracting(ConsultDocs::getId)
                    .containsExactlyInAnyOrder(
                            fixture.wrongPatientEForm.getId(),
                            fixture.missingEForm.getId(),
                            fixture.missingDocument.getId(),
                            fixture.deletedDocument.getId(),
                            fixture.wrongPatientDocument.getId());
            assertThat(consultDocsDao.countStaleActiveConsultAttachments()).isEqualTo(5);
        }

        @Test
        @DisplayName("should soft-delete only invalid active eForm and document attachments")
        void shouldSoftDeleteOnlyInvalidActiveEFormAndDocumentAttachments() {
            CleanupFixture fixture = createCleanupFixture();

            int updated = consultDocsDao.markStaleActiveConsultAttachmentsDeleted();
            entityManager.flush();
            entityManager.clear();

            assertThat(updated).isEqualTo(5);
            assertThat(deletedValue(fixture.validSamePatientEForm)).isNull();
            assertThat(deletedValue(fixture.patientIndependentEForm)).isNull();
            assertThat(deletedValue(fixture.validDocument)).isNull();
            assertThat(deletedValue(fixture.validNonDeletedDocument)).isNull();
            assertThat(deletedValue(fixture.activeLabWithMissingTarget)).isNull();
            assertThat(deletedValue(fixture.activeFormWithMissingTarget)).isNull();
            assertThat(deletedValue(fixture.activeHrmWithMissingTarget)).isNull();
            assertThat(deletedValue(fixture.alreadyDeletedWrongPatientEForm)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.alreadyDeletedMissingDocument)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.wrongPatientEForm)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.missingEForm)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.missingDocument)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.deletedDocument)).isEqualTo(ConsultDocs.DELETED);
            assertThat(deletedValue(fixture.wrongPatientDocument)).isEqualTo(ConsultDocs.DELETED);
        }

        private CleanupFixture createCleanupFixture() {
            int demographicNo = 81001;
            int otherDemographicNo = 81002;
            createDemographic(demographicNo);
            createDemographic(otherDemographicNo);
            ConsultationRequest consult = createConsultationRequest(demographicNo);

            EFormData samePatientEForm = createEFormData(demographicNo, false);
            EFormData patientIndependentEForm = createEFormData(otherDemographicNo, true);
            EFormData wrongPatientEForm = createEFormData(otherDemographicNo, false);

            Document validDocument = createDocument(Document.STATUS_ACTIVE);
            createCtlDocument(demographicNo, validDocument.getDocumentNo());

            Document validNonDeletedDocument = createDocument('S');
            createCtlDocument(demographicNo, validNonDeletedDocument.getDocumentNo(), "S");

            Document deletedDocument = createDocument(Document.STATUS_DELETED);
            createCtlDocument(demographicNo, deletedDocument.getDocumentNo());

            Document wrongPatientDocument = createDocument(Document.STATUS_ACTIVE);
            createCtlDocument(otherDemographicNo, wrongPatientDocument.getDocumentNo());

            CleanupFixture fixture = new CleanupFixture();
            fixture.validSamePatientEForm = createConsultDoc(consult.getId(), samePatientEForm.getId(), ConsultDocs.DOCTYPE_EFORM, null);
            fixture.patientIndependentEForm = createConsultDoc(consult.getId(), patientIndependentEForm.getId(), ConsultDocs.DOCTYPE_EFORM, null);
            fixture.wrongPatientEForm = createConsultDoc(consult.getId(), wrongPatientEForm.getId(), ConsultDocs.DOCTYPE_EFORM, null);
            fixture.missingEForm = createConsultDoc(consult.getId(), 990001, ConsultDocs.DOCTYPE_EFORM, null);
            fixture.alreadyDeletedWrongPatientEForm = createConsultDoc(consult.getId(), wrongPatientEForm.getId(), ConsultDocs.DOCTYPE_EFORM, ConsultDocs.DELETED);
            fixture.validDocument = createConsultDoc(consult.getId(), validDocument.getDocumentNo(), ConsultDocs.DOCTYPE_DOC, null);
            fixture.validNonDeletedDocument = createConsultDoc(consult.getId(), validNonDeletedDocument.getDocumentNo(), ConsultDocs.DOCTYPE_DOC, null);
            fixture.missingDocument = createConsultDoc(consult.getId(), 990002, ConsultDocs.DOCTYPE_DOC, null);
            fixture.deletedDocument = createConsultDoc(consult.getId(), deletedDocument.getDocumentNo(), ConsultDocs.DOCTYPE_DOC, null);
            fixture.wrongPatientDocument = createConsultDoc(consult.getId(), wrongPatientDocument.getDocumentNo(), ConsultDocs.DOCTYPE_DOC, null);
            fixture.alreadyDeletedMissingDocument = createConsultDoc(consult.getId(), 990003, ConsultDocs.DOCTYPE_DOC, ConsultDocs.DELETED);
            fixture.activeLabWithMissingTarget = createConsultDoc(consult.getId(), 990004, ConsultDocs.DOCTYPE_LAB, null);
            fixture.activeFormWithMissingTarget = createConsultDoc(consult.getId(), 990005, ConsultDocs.DOCTYPE_FORM, null);
            fixture.activeHrmWithMissingTarget = createConsultDoc(consult.getId(), 990006, ConsultDocs.DOCTYPE_HRM, null);
            return fixture;
        }
    }

    private static class CleanupFixture {
        private ConsultDocs validSamePatientEForm;
        private ConsultDocs patientIndependentEForm;
        private ConsultDocs wrongPatientEForm;
        private ConsultDocs missingEForm;
        private ConsultDocs alreadyDeletedWrongPatientEForm;
        private ConsultDocs validDocument;
        private ConsultDocs validNonDeletedDocument;
        private ConsultDocs missingDocument;
        private ConsultDocs deletedDocument;
        private ConsultDocs wrongPatientDocument;
        private ConsultDocs alreadyDeletedMissingDocument;
        private ConsultDocs activeLabWithMissingTarget;
        private ConsultDocs activeFormWithMissingTarget;
        private ConsultDocs activeHrmWithMissingTarget;
    }
}
