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

import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link InboxResultsDao}.
 *
 * <p>{@code InboxResultsDao} is a Type C standalone DAO (not extending
 * {@code AbstractDaoImpl} or {@code HibernateDaoSupport}) with direct
 * {@code EntityManager} injection. ALL methods use native SQL queries
 * referencing tables: {@code hl7TextInfo}, {@code patientLabRouting},
 * {@code consultdocs}, {@code providerLabRouting}, {@code document},
 * {@code ctl_document}, {@code demographic}.</p>
 *
 * <p>All referenced tables are now created by hbm2ddl through registered
 * JPA entities (ProviderLabRoutingModel, Hl7TextInfo, PatientLabRouting,
 * ConsultDocs, Document, CtlDocument, Demographic). The test inserts data
 * using both JPA EntityManager and native SQL INSERT statements.</p>
 *
 * <p>Critical for Hibernate 6 migration because:
 * <ul>
 *   <li>All queries are native SQL — schema changes or column type differences will break them</li>
 *   <li>{@code populateDocumentResultsData} dynamically constructs SQL with many conditional branches</li>
 *   <li>{@code populateDocumentResultsData} calls {@code SpringUtils.getBean(SystemPreferencesDao.class)}
 *       and {@code SpringUtils.getBean(DocumentDao.class)} internally</li>
 *   <li>Return types use {@code Object[]} arrays with position-dependent column access</li>
 * </ul>
 *
 * <p><strong>SQL Injection Risk</strong>: {@code isSentToProvider} uses
 * string concatenation instead of parameterized queries. This is a known
 * security issue documented here for future remediation.</p>
 *
 * @since 2026-03-04
 * @see InboxResultsDao
 * @see InboxResultsDaoImpl
 */
@DisplayName("InboxResultsDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("inbox")
@Transactional
public class InboxResultsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private InboxResultsDao inboxResultsDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static final String PROVIDER_NO = "999001";
    private static final Integer DEMO_ID = 2001;

    private Date today;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.MARCH, 4, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        today = cal.getTime();

        // Create a demographic record (HBM-mapped)
        createDemographic(DEMO_ID);
    }

    private void createDemographic(Integer demoNo) {
        Demographic demo = new Demographic();
        demo.setDemographicNo(demoNo);
        demo.setFirstName("Test");
        demo.setLastName("Patient");
        demo.setHin("1234567890");
        demo.setSex("M");
        demo.setProviderNo(PROVIDER_NO);
        demo.setPatientStatus("AC");
        demo.setPatientStatusDate(today);
        demo.setDateJoined(today);
        hibernateTemplate.save(demo);
        hibernateTemplate.flush();
    }

    /**
     * Creates a ProviderLabRouting entry linking a document to a provider.
     */
    private ProviderLabRoutingModel createProviderLabRouting(String providerNo, Integer labNo, String labType, String status) {
        ProviderLabRoutingModel plr = new ProviderLabRoutingModel();
        plr.setProviderNo(providerNo);
        plr.setLabNo(labNo);
        plr.setLabType(labType);
        plr.setStatus(status);
        plr.setTimestamp(today);
        entityManager.persist(plr);
        entityManager.flush();
        return plr;
    }

    /**
     * Creates a Document entity.
     */
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

    // ========================================================================
    // Bean Wiring
    // ========================================================================

    @Nested
    @DisplayName("Bean Wiring")
    class BeanWiring {

        @Test
        @DisplayName("should autowire InboxResultsDao bean")
        void shouldAutowireBean() {
            // Then
            assertThat(inboxResultsDao).isNotNull();
            assertThat(inboxResultsDao).isInstanceOf(InboxResultsDaoImpl.class);
        }
    }

    // ========================================================================
    // isSentToProvider
    // ========================================================================

    @Nested
    @DisplayName("isSentToProvider")
    @Tag("read")
    class IsSentToProvider {

        @Test
        @DisplayName("should return true when document is sent to provider")
        void shouldReturnTrue_whenDocSentToProvider() {
            // Given
            createProviderLabRouting(PROVIDER_NO, 100, "DOC", "N");

            // When
            boolean result = inboxResultsDao.isSentToProvider("100", PROVIDER_NO);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when document is not sent to provider")
        void shouldReturnFalse_whenDocNotSentToProvider() {
            // When
            boolean result = inboxResultsDao.isSentToProvider("99999", PROVIDER_NO);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when docNo is null")
        void shouldReturnFalse_whenDocNoIsNull() {
            // When
            boolean result = inboxResultsDao.isSentToProvider(null, PROVIDER_NO);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when providerNo is null")
        void shouldReturnFalse_whenProviderNoIsNull() {
            // When
            boolean result = inboxResultsDao.isSentToProvider("100", null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should only match DOC lab_type entries")
        void shouldOnlyMatchDocLabType() {
            // Given — entry with HL7 lab_type
            createProviderLabRouting(PROVIDER_NO, 200, "HL7", "N");

            // When
            boolean result = inboxResultsDao.isSentToProvider("200", PROVIDER_NO);

            // Then — HL7 entries should not match (query filters lab_type='DOC')
            assertThat(result).isFalse();
        }
    }

    // ========================================================================
    // populateHL7ResultsData
    // ========================================================================

    @Nested
    @DisplayName("populateHL7ResultsData")
    @Tag("read")
    class PopulateHL7ResultsData {

        @Test
        @DisplayName("should return empty list when no matching labs")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenNoMatchingLabs() {
            // When
            ArrayList result = inboxResultsDao.populateHL7ResultsData("99999", "1", false);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when demographic has no lab routing")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenNoLabRouting() {
            // Given — demographic exists but no patientLabRouting entries
            // When
            ArrayList result = inboxResultsDao.populateHL7ResultsData(String.valueOf(DEMO_ID), "1", false);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // populateDocumentResultsData — simplest overload (non-paged)
    // ========================================================================

    @Nested
    @DisplayName("populateDocumentResultsData (simple)")
    @Tag("read")
    class PopulateDocumentResultsDataSimple {

        @Test
        @DisplayName("should return empty list when no documents exist")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenNoDocuments() {
            // When
            ArrayList result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for provider with no routed documents")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_forProviderWithNoRoutedDocs() {
            // Given — document exists but no providerLabRouting to this provider
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());

            // When
            ArrayList result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "");

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // populateDocumentResultsData — full overload with demographicNo
    // ========================================================================

    @Nested
    @DisplayName("populateDocumentResultsData (full, !mixLabsAndDocs)")
    @Tag("read")
    class PopulateDocumentResultsDataFull {

        @Test
        @Disabled("Requires DocumentDao and other beans in test context (SpringUtils.getBean calls in populateDocumentResultsData)")
        @DisplayName("should return documents for specific demographic when routing exists")
        @SuppressWarnings("unchecked")
        void shouldReturnDocuments_forDemographicWithRouting() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            // When — !mixLabsAndDocs, specific demographicNo
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "",
                    false, null, null, false, null);

            // Then
            assertThat(result).hasSize(1);
            LabResultData lrd = result.get(0);
            assertThat(lrd.labType).isEqualTo(LabResultData.DOCUMENT);
            assertThat(lrd.segmentID).isEqualTo(String.valueOf(doc.getDocumentNo()));
        }

        @Test
        @Disabled("Requires DocumentDao and other beans in test context (SpringUtils.getBean calls in populateDocumentResultsData)")
        @DisplayName("should return documents filtered by status")
        @SuppressWarnings("unchecked")
        void shouldReturnDocuments_filteredByStatus() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            // When — filter by status "N"
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "N",
                    false, null, null, false, null);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when status doesn't match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenStatusDoesntMatch() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            // When — filter by status "A" (but routing has "N")
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "A",
                    false, null, null, false, null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when provider doesn't match")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            // When — different provider
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    "OTHER", String.valueOf(DEMO_ID), "", "", "", "",
                    false, null, null, false, null);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // populateDocumentResultsData — unmatched demographics (demoNo="0")
    // ========================================================================

    @Nested
    @DisplayName("populateDocumentResultsData (unmatched, demographicNo=0)")
    @Tag("read")
    class PopulateDocumentResultsDataUnmatched {

        @Test
        @DisplayName("should return unmatched documents when demographicNo is 0")
        @SuppressWarnings("unchecked")
        void shouldReturnUnmatchedDocs_whenDemoNoIsZero() {
            // Given — document linked to moduleId=-1 (unmatched)
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", -1, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            // When — demographicNo="0" triggers unmatched documents path
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, "0", "", "", "", "",
                    false, null, null, false, null);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).labType).isEqualTo(LabResultData.DOCUMENT);
        }

        @Test
        @DisplayName("should return empty when no unmatched documents")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenNoUnmatchedDocs() {
            // Given — no documents at all

            // When
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, "0", "", "", "", "",
                    false, null, null, false, null);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========================================================================
    // populateDocumentResultsData — date filtering
    // ========================================================================

    @Nested
    @DisplayName("populateDocumentResultsData (date filtering)")
    @Tag("read")
    @Tag("filter")
    class PopulateDocumentResultsDataDateFiltering {

        @Test
        @Disabled("Requires DocumentDao and other beans in test context (SpringUtils.getBean calls in populateDocumentResultsData)")
        @DisplayName("should filter documents by start and end dates")
        @SuppressWarnings("unchecked")
        void shouldFilterDocuments_byDateRange() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            Calendar cal = Calendar.getInstance();
            cal.set(2026, Calendar.MARCH, 1, 0, 0, 0);
            Date startDate = cal.getTime();
            cal.set(2026, Calendar.MARCH, 31, 23, 59, 59);
            Date endDate = cal.getTime();

            // When — with date range that includes the document
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "",
                    false, null, null, false, null, startDate, endDate);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when documents are outside date range")
        @SuppressWarnings("unchecked")
        void shouldReturnEmpty_whenOutsideDateRange() {
            // Given
            Document doc = createDocument("lab", PROVIDER_NO, 'A');
            createCtlDocument("demographic", DEMO_ID, doc.getDocumentNo());
            createProviderLabRouting(PROVIDER_NO, doc.getDocumentNo(), "DOC", "N");

            Calendar cal = Calendar.getInstance();
            cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
            Date startDate = cal.getTime();
            cal.set(2025, Calendar.JANUARY, 31, 23, 59, 59);
            Date endDate = cal.getTime();

            // When — date range before document creation
            ArrayList<LabResultData> result = inboxResultsDao.populateDocumentResultsData(
                    PROVIDER_NO, String.valueOf(DEMO_ID), "", "", "", "",
                    false, null, null, false, null, startDate, endDate);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
