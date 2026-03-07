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

import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DocumentDao} query methods.
 *
 * <p>Validates JPQL and native SQL queries for document management. Covers
 * Document-only lookups, CtlDocument join queries (Object[] returns),
 * ConsultDocs/EFormDocs/ConsultResponseDoc cross-entity queries, date-based
 * queries, aggregate queries, and native SQL with ctl_document joins.</p>
 *
 * <p>Critical for Hibernate 6 migration due to:
 * <ul>
 *   <li>Object[] return types from JPQL cross-entity queries</li>
 *   <li>EAGER {@code @OneToMany} relationship with DocumentReview</li>
 *   <li>Composite embedded ID traversal (CtlDocumentPK: c.id.module, c.id.moduleId, c.id.documentNo)</li>
 *   <li>Native SQL with BigInteger return from COUNT(*)</li>
 *   <li>DISTINCT queries with cross-entity joins</li>
 * </ul>
 *
 * <p><strong>Known bugs documented by tests:</strong>
 * <ul>
 *   <li>{@code findByDemographicAndDoctype}: native SQL parameter order swapped (param 1 = demographicId, param 2 = doctype name, but query expects doctype first)</li>
 *   <li>{@code findByDemographicAndFilename}: native SQL parameter order swapped (param 1 = demographicId, param 2 = filename, but query expects filename first)</li>
 * </ul>
 *
 * <p><strong>Not tested:</strong> {@code findDocuments} (deprecated, requires EDocUtil static
 * initialization with many SpringUtils beans), {@code findConstultDocsDocsAndProvidersByModule}
 * (requires HBM Provider entity access via {@code p.ProviderNo} HQL property),
 * {@code findCtlDocsAndDocsByModuleCreatorResponsibleAndDates} (has JPQL syntax bugs:
 * missing space before AND, uses {@code c.documentNo} instead of {@code c.id.documentNo}).</p>
 *
 * @since 2026-03-04
 * @see DocumentDao
 * @see DocumentDaoImpl
 */
@DisplayName("DocumentDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("document")
@Transactional
public class DocumentDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DocumentDao documentDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final Integer DEMO_ID = 1001;

    private Date today;
    private Date yesterday;
    private Date lastWeek;
    private Date tomorrow;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        cal.add(Calendar.DAY_OF_MONTH, -1);
        yesterday = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, -7);
        lastWeek = cal.getTime();

        cal.setTime(today);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        tomorrow = cal.getTime();

        // Create a demographic record (HBM-mapped, use hibernateTemplate)
        createDemographic(DEMO_ID);
    }

    /**
     * Creates a Demographic record with a specific demographic_no.
     * Uses native SQL because Demographic has an identity generator that ignores manually set IDs.
     */
    private static final String INSERT_DEMO_SQL = "INSERT INTO demographic (demographic_no, first_name, last_name, sex, provider_no, patient_status) VALUES (:id, 'Test', 'Patient', 'M', :provNo, 'AC')";

    private void createDemographic(Integer demoNo) {
        entityManager.createNativeQuery(INSERT_DEMO_SQL)
                .setParameter("id", demoNo)
                .setParameter("provNo", PROVIDER_NO)
                .executeUpdate();
        entityManager.flush();
    }

    private Document createDocument(String doctype, String creator, char status) {
        Document doc = new Document();
        doc.setDoctype(doctype);
        doc.setDocdesc("Test document");
        doc.setDocfilename("test.pdf");
        doc.setDoccreator(creator);
        doc.setResponsible(creator);
        doc.setStatus(status);
        doc.setContenttype("application/pdf");
        doc.setPublic1(0);
        doc.setNumberofpages(1);
        doc.setObservationdate(today);
        doc.setUpdatedatetime(today);
        doc.setContentdatetime(today);
        return doc;
    }

    private Document createAndPersist(String doctype, String creator, char status) {
        Document doc = createDocument(doctype, creator, status);
        entityManager.persist(doc);
        entityManager.flush();
        return doc;
    }

    /**
     * Creates a CtlDocument linking a document to a module/moduleId.
     */
    private CtlDocument createCtlDocument(String module, Integer moduleId, Integer documentNo) {
        CtlDocument ctl = new CtlDocument();
        ctl.setId(new CtlDocumentPK(module, moduleId, documentNo));
        ctl.setStatus("A");
        entityManager.persist(ctl);
        entityManager.flush();
        return ctl;
    }

    /**
     * Creates a Document + CtlDocument pair linked to a demographic.
     */
    private Document createDocumentWithCtl(String doctype, String creator, char status, Integer demoId) {
        Document doc = createAndPersist(doctype, creator, status);
        createCtlDocument("demographic", demoId, doc.getDocumentNo());
        return doc;
    }

    // ========================================================================
    // findActiveByDocumentNo
    // ========================================================================

    @Nested
    @DisplayName("findActiveByDocumentNo")
    @Tag("read")
    class FindActiveByDocumentNo {

        @Test
        @DisplayName("should return document by document number")
        void shouldReturnDocument_byDocumentNo() {
            // Given
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            List<Document> result = documentDao.findActiveByDocumentNo(doc.getDocumentNo());

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDocumentNo()).isEqualTo(doc.getDocumentNo());
        }

        @Test
        @DisplayName("should return empty list for non-existent document")
        void shouldReturnEmpty_forNonExistentDocument() {
            // When
            List<Document> result = documentDao.findActiveByDocumentNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findMaxDocNo (aggregate)
    // ========================================================================

    @Nested
    @DisplayName("findMaxDocNo")
    @Tag("aggregate")
    class FindMaxDocNo {

        @Test
        @DisplayName("should return maximum document number")
        void shouldReturnMaxDocumentNo() {
            // Given
            Document doc1 = createAndPersist("lab", PROVIDER_NO, 'A');
            Document doc2 = createAndPersist("consult", PROVIDER_NO, 'A');
            int expected = Math.max(doc1.getDocumentNo(), doc2.getDocumentNo());

            // When
            Integer result = documentDao.findMaxDocNo();

            // Then
            assertThat(result).isGreaterThanOrEqualTo(expected);
        }

        @Test
        @DisplayName("should return 0 when no documents exist")
        void shouldReturnZero_whenNoDocuments() {
            // When
            Integer result = documentDao.findMaxDocNo();

            // Then
            assertThat(result).isEqualTo(0);
        }
    }

    // ========================================================================
    // getDocument
    // ========================================================================

    @Nested
    @DisplayName("getDocument")
    @Tag("read")
    class GetDocument {

        @Test
        @DisplayName("should return document by string ID")
        void shouldReturnDocument_byStringId() {
            // Given
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            Document result = documentDao.getDocument(String.valueOf(doc.getDocumentNo()));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDocumentNo()).isEqualTo(doc.getDocumentNo());
        }

        @Test
        @DisplayName("should return null for non-numeric string")
        void shouldReturnNull_forNonNumericString() {
            // When
            Document result = documentDao.getDocument("abc");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for non-existent ID")
        void shouldReturnNull_forNonExistentId() {
            // When
            Document result = documentDao.getDocument("99999");

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // subtractPages
    // ========================================================================

    @Nested
    @DisplayName("subtractPages")
    @Tag("update")
    class SubtractPages {

        @Test
        @DisplayName("should subtract pages from document")
        void shouldSubtractPages_fromDocument() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            doc.setNumberofpages(5);
            entityManager.persist(doc);
            entityManager.flush();

            // When
            documentDao.subtractPages(String.valueOf(doc.getDocumentNo()), 2);
            entityManager.flush();
            entityManager.clear();

            // Then
            Document refreshed = entityManager.find(Document.class, doc.getDocumentNo());
            assertThat(refreshed.getNumberofpages()).isEqualTo(3);
        }
    }

    // ========================================================================
    // findByUpdateDate
    // ========================================================================

    @Nested
    @DisplayName("findByUpdateDate")
    @Tag("read")
    class FindByUpdateDate {

        @Test
        @DisplayName("should return documents updated after date")
        void shouldReturnDocuments_updatedAfterDate() {
            // Given
            createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            List<Document> result = documentDao.findByUpdateDate(yesterday, 10);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDoctype()).isEqualTo("lab");
        }

        @Test
        @DisplayName("should respect items limit")
        void shouldRespectLimit_forUpdateDate() {
            // Given
            createAndPersist("lab", PROVIDER_NO, 'A');
            createAndPersist("consult", PROVIDER_NO, 'A');

            // When
            List<Document> result = documentDao.findByUpdateDate(lastWeek, 1);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findByDoctype
    // ========================================================================

    @Nested
    @DisplayName("findByDoctype")
    @Tag("read")
    class FindByDoctype {

        @Test
        @DisplayName("should return active documents by doctype")
        void shouldReturnActiveDocuments_byDoctype() {
            // Given
            createAndPersist("lab", PROVIDER_NO, 'A');
            createAndPersist("lab", PROVIDER_NO, 'D');

            // When
            List<Document> result = documentDao.findByDoctype("lab");

            // Then
            assertThat(result).isNotEmpty();
            assertThat(result).allSatisfy(d ->
                    assertThat(d.getStatus()).isEqualTo('A'));
        }

        @Test
        @DisplayName("should return empty for non-existent doctype")
        void shouldReturnEmpty_forNonExistentDoctype() {
            // When
            List<Document> result = documentDao.findByDoctype("ZZZZZ");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDoctypeAndProviderNo
    // ========================================================================

    @Nested
    @DisplayName("findByDoctypeAndProviderNo")
    @Tag("read")
    class FindByDoctypeAndProviderNo {

        @Test
        @DisplayName("should return documents by doctype, provider, and public flag")
        void shouldReturnDocuments_byDoctypeProviderAndPublic() {
            // Given
            createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            List<Document> result = documentDao.findByDoctypeAndProviderNo("lab", PROVIDER_NO, 0);

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            // Given
            createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            List<Document> result = documentDao.findByDoctypeAndProviderNo("lab", "NONE", 0);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getCtlDocsAndDocsByDemoId — Object[] return (CtlDocument, Document)
    // ========================================================================

    @Nested
    @DisplayName("getCtlDocsAndDocsByDemoId")
    @Tag("read")
    class GetCtlDocsAndDocsByDemoId {

        @Test
        @DisplayName("should return CtlDocument and Document pairs for demographic")
        void shouldReturnPairs_forDemographic() {
            // Given
            Document doc = createDocumentWithCtl("consult", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Object[]> result = documentDao.getCtlDocsAndDocsByDemoId(
                    DEMO_ID, DocumentDao.Module.DEMOGRAPHIC, DocumentDao.DocumentType.CONSULT);

            // Then
            assertThat(result).hasSize(1);
            Object[] row = result.get(0);
            assertThat(row).hasSize(2);
            assertThat(row[0]).isInstanceOf(CtlDocument.class);
            assertThat(row[1]).isInstanceOf(Document.class);
            assertThat(((Document) row[1]).getDocumentNo()).isEqualTo(doc.getDocumentNo());
        }

        @Test
        @DisplayName("should return empty when no matching doctype")
        void shouldReturnEmpty_whenNoMatchingDoctype() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Object[]> result = documentDao.getCtlDocsAndDocsByDemoId(
                    DEMO_ID, DocumentDao.Module.DEMOGRAPHIC, DocumentDao.DocumentType.CONSULT);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent demographic")
        void shouldReturnEmpty_forNonExistentDemographic() {
            // When
            List<Object[]> result = documentDao.getCtlDocsAndDocsByDemoId(
                    99999, DocumentDao.Module.DEMOGRAPHIC, DocumentDao.DocumentType.CONSULT);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findCtlDocsAndDocsByModuleDocTypeAndModuleId
    // ========================================================================

    @Nested
    @DisplayName("findCtlDocsAndDocsByModuleDocTypeAndModuleId")
    @Tag("read")
    class FindCtlDocsAndDocsByModuleDocTypeAndModuleId {

        @Test
        @DisplayName("should return Object[] pairs by module, doctype, and moduleId")
        void shouldReturnPairs_byModuleDocTypeAndModuleId() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Object[]> result = documentDao.findCtlDocsAndDocsByModuleDocTypeAndModuleId(
                    DocumentDao.Module.DEMOGRAPHIC, DocumentDao.DocumentType.LAB, DEMO_ID);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[1]).isInstanceOf(Document.class);
        }

        @Test
        @DisplayName("should return empty when module doesn't match")
        void shouldReturnEmpty_whenModuleDoesntMatch() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When — query with CONSULT doctype but document is "lab"
            List<Object[]> result = documentDao.findCtlDocsAndDocsByModuleDocTypeAndModuleId(
                    DocumentDao.Module.DEMOGRAPHIC, DocumentDao.DocumentType.CONSULT, DEMO_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findCtlDocsAndDocsByModuleAndModuleId
    // ========================================================================

    @Nested
    @DisplayName("findCtlDocsAndDocsByModuleAndModuleId")
    @Tag("read")
    class FindCtlDocsAndDocsByModuleAndModuleId {

        @Test
        @DisplayName("should return active documents by module and moduleId with matching status")
        void shouldReturnActive_byModuleAndModuleId() {
            // Given — CtlDocument status must match Document status, and status != 'D'
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');
            CtlDocument ctl = new CtlDocument();
            ctl.setId(new CtlDocumentPK("demographic", DEMO_ID, doc.getDocumentNo()));
            ctl.setStatus("A");
            entityManager.persist(ctl);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findCtlDocsAndDocsByModuleAndModuleId(
                    DocumentDao.Module.DEMOGRAPHIC, DEMO_ID);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should exclude deleted documents")
        void shouldExcludeDeleted_byModuleAndModuleId() {
            // Given — document with status 'D' should be excluded
            Document doc = createAndPersist("lab", PROVIDER_NO, 'D');
            CtlDocument ctl = new CtlDocument();
            ctl.setId(new CtlDocumentPK("demographic", DEMO_ID, doc.getDocumentNo()));
            ctl.setStatus("D");
            entityManager.persist(ctl);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findCtlDocsAndDocsByModuleAndModuleId(
                    DocumentDao.Module.DEMOGRAPHIC, DEMO_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findCtlDocsAndDocsByDocNo
    // ========================================================================

    @Nested
    @DisplayName("findCtlDocsAndDocsByDocNo")
    @Tag("read")
    class FindCtlDocsAndDocsByDocNo {

        @Test
        @DisplayName("should return Document and CtlDocument pair by document number")
        void shouldReturnPair_byDocumentNo() {
            // Given
            Document doc = createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Object[]> result = documentDao.findCtlDocsAndDocsByDocNo(doc.getDocumentNo());

            // Then
            assertThat(result).hasSize(1);
            // Note: query is "FROM Document d, CtlDocument c" so d is [0], c is [1]
            assertThat(result.get(0)[0]).isInstanceOf(Document.class);
            assertThat(result.get(0)[1]).isInstanceOf(CtlDocument.class);
        }

        @Test
        @DisplayName("should return empty for non-existent document number")
        void shouldReturnEmpty_forNonExistentDocNo() {
            // When
            List<Object[]> result = documentDao.findCtlDocsAndDocsByDocNo(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicId — DISTINCT Document via CtlDocument join
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicId")
    @Tag("read")
    class FindByDemographicId {

        @Test
        @DisplayName("should return distinct active documents for demographic")
        void shouldReturnDistinctActive_forDemographic() {
            // Given — active document linked to demographic via CtlDocument
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');
            CtlDocument ctl = new CtlDocument();
            ctl.setId(new CtlDocumentPK("demographic", DEMO_ID, doc.getDocumentNo()));
            ctl.setStatus("A");
            entityManager.persist(ctl);
            entityManager.flush();

            // When
            List<Document> result = documentDao.findByDemographicId(String.valueOf(DEMO_ID));

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDocumentNo()).isEqualTo(doc.getDocumentNo());
        }

        @Test
        @DisplayName("should exclude deleted documents")
        void shouldExcludeDeleted_forDemographic() {
            // Given — deleted document
            Document doc = createAndPersist("lab", PROVIDER_NO, 'D');
            CtlDocument ctl = new CtlDocument();
            ctl.setId(new CtlDocumentPK("demographic", DEMO_ID, doc.getDocumentNo()));
            ctl.setStatus("D");
            entityManager.persist(ctl);
            entityManager.flush();

            // When
            List<Document> result = documentDao.findByDemographicId(String.valueOf(DEMO_ID));

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for non-numeric demographic ID")
        void shouldReturnEmpty_forNonNumericDemoId() {
            // When
            List<Document> result = documentDao.findByDemographicId("abc");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicUpdateDate — CtlDocument join with date filter
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicUpdateDate")
    @Tag("read")
    class FindByDemographicUpdateDate {

        @Test
        @DisplayName("should return documents updated on or after given date")
        void shouldReturnDocuments_updatedOnOrAfterDate() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When — inclusive, document updated at 'today'
            List<Document> result = documentDao.findByDemographicUpdateDate(DEMO_ID, today);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when no documents updated since date")
        void shouldReturnEmpty_whenNoUpdates() {
            // Given — @PrePersist overrides updatedatetime with now(), so re-set it
            Document doc = createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);
            doc.setUpdatedatetime(today);
            entityManager.merge(doc);
            entityManager.flush();

            // When — tomorrow is after document update
            List<Document> result = documentDao.findByDemographicUpdateDate(DEMO_ID, tomorrow);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicUpdateAfterDate — CtlDocument join, exclusive date
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicUpdateAfterDate")
    @Tag("read")
    class FindByDemographicUpdateAfterDate {

        @Test
        @DisplayName("should return documents updated strictly after given date")
        void shouldReturnDocuments_updatedAfterDate() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When — exclusive: yesterday < today
            List<Document> result = documentDao.findByDemographicUpdateAfterDate(DEMO_ID, yesterday);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should exclude documents updated at exactly the given date")
        void shouldExcludeDocuments_updatedAtExactDate() {
            // Given — @PrePersist overrides updatedatetime with now(), so re-set it
            Document doc = createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);
            doc.setUpdatedatetime(today);
            entityManager.merge(doc);
            entityManager.flush();

            // When — exclusive: today == today, should NOT include
            List<Document> result = documentDao.findByDemographicUpdateAfterDate(DEMO_ID, today);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByProgramProviderDemographicUpdateDate
    // ========================================================================

    @Nested
    @DisplayName("findByProgramProviderDemographicUpdateDate")
    @Tag("read")
    class FindByProgramProviderDemographicUpdateDate {

        @Test
        @DisplayName("should return documents by program, provider, demographic, and date")
        void shouldReturnDocuments_byAllCriteria() {
            // Given — document with null programId (matches "or d.programId is null")
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Document> result = documentDao.findByProgramProviderDemographicUpdateDate(
                    1, PROVIDER_NO, DEMO_ID, yesterday, 10);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Document> result = documentDao.findByProgramProviderDemographicUpdateDate(
                    1, "OTHER", DEMO_ID, yesterday, 10);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should respect items limit")
        void shouldRespectLimit() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);
            createDocumentWithCtl("consult", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Document> result = documentDao.findByProgramProviderDemographicUpdateDate(
                    1, PROVIDER_NO, DEMO_ID, lastWeek, 1);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // ========================================================================
    // findDemographicIdsSince — returns moduleId from CtlDocument
    // ========================================================================

    @Nested
    @DisplayName("findDemographicIdsSince")
    @Tag("read")
    class FindDemographicIdsSince {

        @Test
        @DisplayName("should return demographic IDs from documents updated since date")
        void shouldReturnDemoIds_sinceDate() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Integer> result = documentDao.findDemographicIdsSince(yesterday);

            // Then
            assertThat(result).contains(DEMO_ID);
        }

        @Test
        @DisplayName("should return all demographic IDs when date is null")
        void shouldReturnAllDemoIds_whenDateIsNull() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            List<Integer> result = documentDao.findDemographicIdsSince(null);

            // Then
            assertThat(result).contains(DEMO_ID);
        }

        @Test
        @DisplayName("should return empty when no updates since date")
        void shouldReturnEmpty_whenNoUpdates() {
            // Given — @PrePersist overrides updatedatetime with now(), so re-set it
            Document doc = createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);
            doc.setUpdatedatetime(today);
            entityManager.merge(doc);
            entityManager.flush();

            // When
            List<Integer> result = documentDao.findDemographicIdsSince(tomorrow);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // getDemoFromDocNo — returns Demographic via CtlDocument join
    // ========================================================================

    @Nested
    @DisplayName("getDemoFromDocNo")
    @Tag("read")
    class GetDemoFromDocNo {

        @Test
        @DisplayName("should return demographic linked to document")
        void shouldReturnDemographic_linkedToDocument() {
            // Given
            Document doc = createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            Demographic result = documentDao.getDemoFromDocNo(String.valueOf(doc.getDocumentNo()));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDemographicNo()).isEqualTo(DEMO_ID);
        }

        @Test
        @DisplayName("should return null for non-numeric doc number")
        void shouldReturnNull_forNonNumericDocNo() {
            // When
            Demographic result = documentDao.getDemoFromDocNo("abc");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when document has no demographic link")
        void shouldReturnNull_whenNoLink() {
            // Given — document without CtlDocument link
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');

            // When
            Demographic result = documentDao.getDemoFromDocNo(String.valueOf(doc.getDocumentNo()));

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when CtlDocument moduleId is -1 (unmatched)")
        void shouldReturnNull_whenUnmatchedDemographic() {
            // Given — CtlDocument with moduleId = -1 (unmatched)
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", -1, doc.getDocumentNo());

            // When
            Demographic result = documentDao.getDemoFromDocNo(String.valueOf(doc.getDocumentNo()));

            // Then
            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // getNumberOfDocumentsAttachedToAProviderDemographics — native SQL COUNT
    // ========================================================================

    @Nested
    @DisplayName("getNumberOfDocumentsAttachedToAProviderDemographics")
    @Tag("read")
    @Tag("aggregate")
    class GetNumberOfDocumentsAttachedToAProviderDemographics {

        @Test
        @DisplayName("should return count of documents for provider's demographics")
        void shouldReturnCount_forProviderDemographics() {
            // Given — demographic with provider, document linked via ctl_document
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When
            int result = documentDao.getNumberOfDocumentsAttachedToAProviderDemographics(
                    PROVIDER_NO, lastWeek, tomorrow);

            // Then
            assertThat(result).isEqualTo(1);
        }

        @Test
        @DisplayName("should return zero when no matching documents")
        void shouldReturnZero_whenNoMatching() {
            // When
            int result = documentDao.getNumberOfDocumentsAttachedToAProviderDemographics(
                    "NONE", lastWeek, tomorrow);

            // Then
            assertThat(result).isEqualTo(0);
        }
    }

    // ========================================================================
    // findDocsAndConsultDocsByConsultId — Document + ConsultDocs join
    // ========================================================================

    @Nested
    @DisplayName("findDocsAndConsultDocsByConsultId")
    @Tag("read")
    class FindDocsAndConsultDocsByConsultId {

        @Test
        @DisplayName("should return Document and ConsultDocs pairs for consultation")
        void shouldReturnPairs_forConsultation() {
            // Given
            Document doc = createAndPersist("consult", PROVIDER_NO, 'A');
            ConsultDocs cd = new ConsultDocs();
            cd.setDocumentNo(doc.getDocumentNo());
            cd.setRequestId(100);
            cd.setDocType(ConsultDocs.DOCTYPE_DOC);
            cd.setDeleted(null);
            entityManager.persist(cd);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findDocsAndConsultDocsByConsultId(100);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isInstanceOf(Document.class);
            assertThat(result.get(0)[1]).isInstanceOf(ConsultDocs.class);
        }

        @Test
        @DisplayName("should exclude deleted consult docs")
        void shouldExcludeDeleted_consultDocs() {
            // Given
            Document doc = createAndPersist("consult", PROVIDER_NO, 'A');
            ConsultDocs cd = new ConsultDocs();
            cd.setDocumentNo(doc.getDocumentNo());
            cd.setRequestId(100);
            cd.setDocType(ConsultDocs.DOCTYPE_DOC);
            cd.setDeleted("Y");
            entityManager.persist(cd);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findDocsAndConsultDocsByConsultId(100);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findDocsAndEFormDocsByFdid — Document + EFormDocs join
    // ========================================================================

    @Nested
    @DisplayName("findDocsAndEFormDocsByFdid")
    @Tag("read")
    class FindDocsAndEFormDocsByFdid {

        @Test
        @DisplayName("should return Document and EFormDocs pairs for fdid")
        void shouldReturnPairs_forFdid() {
            // Given
            Document doc = createAndPersist("lab", PROVIDER_NO, 'A');
            EFormDocs efd = new EFormDocs(200, doc.getDocumentNo(), EFormDocs.DOCTYPE_DOC, PROVIDER_NO);
            entityManager.persist(efd);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findDocsAndEFormDocsByFdid(200);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isInstanceOf(Document.class);
            assertThat(result.get(0)[1]).isInstanceOf(EFormDocs.class);
        }

        @Test
        @DisplayName("should return empty when no matching fdid")
        void shouldReturnEmpty_whenNoMatchingFdid() {
            // When
            List<Object[]> result = documentDao.findDocsAndEFormDocsByFdid(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findDocsAndConsultResponseDocsByConsultId — Document + ConsultResponseDoc
    // ========================================================================

    @Nested
    @DisplayName("findDocsAndConsultResponseDocsByConsultId")
    @Tag("read")
    class FindDocsAndConsultResponseDocsByConsultId {

        @Test
        @DisplayName("should return Document and ConsultResponseDoc pairs")
        void shouldReturnPairs_forConsultResponse() {
            // Given
            Document doc = createAndPersist("consult", PROVIDER_NO, 'A');
            ConsultResponseDoc crd = new ConsultResponseDoc();
            crd.setDocumentNo(doc.getDocumentNo());
            crd.setResponseId(300);
            crd.setDocType("D");
            crd.setDeleted(null);
            entityManager.persist(crd);
            entityManager.flush();

            // When
            List<Object[]> result = documentDao.findDocsAndConsultResponseDocsByConsultId(300);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)[0]).isInstanceOf(Document.class);
            assertThat(result.get(0)[1]).isInstanceOf(ConsultResponseDoc.class);
        }

        @Test
        @DisplayName("should return empty for non-existent consultation")
        void shouldReturnEmpty_forNonExistent() {
            // When
            List<Object[]> result = documentDao.findDocsAndConsultResponseDocsByConsultId(99999);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // findByDemographicAndDoctype — native SQL (KNOWN BUG: params swapped)
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicAndDoctype")
    @Tag("read")
    class FindByDemographicAndDoctype {

        @Test
        @DisplayName("should document parameter order bug — params are swapped in implementation")
        void shouldDocumentParamOrderBug() {
            // Given
            createDocumentWithCtl("consult", PROVIDER_NO, 'A', DEMO_ID);

            // When/Then — The implementation has a bug: setParameter(1, demographicId)
            // but position 1 in the SQL is d.doctype = ?1 (expects doctype string).
            // setParameter(2, documentType.getName()) but position 2 is c.module_id = ?2
            // (expects int). H2 throws a DataException due to the type mismatch;
            // MySQL silently returns wrong results.
            assertThatThrownBy(() -> documentDao.findByDemographicAndDoctype(
                    DEMO_ID, DocumentDao.DocumentType.CONSULT))
                    .isInstanceOf(Exception.class);
        }
    }

    // ========================================================================
    // findByDemographicAndFilename — native SQL (KNOWN BUG: params swapped)
    // ========================================================================

    @Nested
    @DisplayName("findByDemographicAndFilename")
    @Tag("read")
    class FindByDemographicAndFilename {

        @Test
        @DisplayName("should document parameter order bug — params are swapped in implementation")
        void shouldDocumentParamOrderBug() {
            // Given
            createDocumentWithCtl("lab", PROVIDER_NO, 'A', DEMO_ID);

            // When/Then — Same bug as findByDemographicAndDoctype:
            // setParameter(1, demographicId) but SQL position 1 is d.docfilename = ?1
            // setParameter(2, fileName) but SQL position 2 is c.module_id = ?2
            // H2 throws a DataException due to the type mismatch;
            // MySQL silently returns wrong results.
            assertThatThrownBy(() -> documentDao.findByDemographicAndFilename(DEMO_ID, "test.pdf"))
                    .isInstanceOf(Exception.class);
        }
    }
}
