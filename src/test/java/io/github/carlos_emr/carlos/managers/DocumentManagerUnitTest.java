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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentManagerImpl} business logic.
 *
 * <p>Tests security enforcement, document retrieval, save logic (new vs update),
 * consent-gated access, queue assignment, and provider acknowledgement filtering.</p>
 *
 * @since 2026-03-31
 * @see DocumentManagerImpl
 * @see DocumentManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("document")
class DocumentManagerUnitTest extends CarlosUnitTestBase {

    @Mock private DocumentDao mockDocumentDao;
    @Mock private CtlDocumentDao mockCtlDocumentDao;
    @Mock private NioFileManager mockNioFileManager;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private ProviderInboxRoutingDao mockProviderInboxRoutingDao;
    @Mock private PatientConsentManager mockPatientConsentManager;
    @Mock private ProviderLabRoutingDao mockProviderLabRoutingDao;
    @Mock private PatientLabRoutingDao mockPatientLabRoutingDao;
    @Mock private QueueDocumentLinkDao mockQueueDocumentLinkDao;

    private DocumentManagerImpl manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(DocumentDao.class, mockDocumentDao);
        registerMock(CtlDocumentDao.class, mockCtlDocumentDao);
        registerMock(NioFileManager.class, mockNioFileManager);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(ProviderInboxRoutingDao.class, mockProviderInboxRoutingDao);
        registerMock(PatientConsentManager.class, mockPatientConsentManager);
        registerMock(ProviderLabRoutingDao.class, mockProviderLabRoutingDao);
        registerMock(PatientLabRoutingDao.class, mockPatientLabRoutingDao);
        registerMock(QueueDocumentLinkDao.class, mockQueueDocumentLinkDao);

        manager = new DocumentManagerImpl();
        injectDependency(manager, "documentDao", mockDocumentDao);
        injectDependency(manager, "ctlDocumentDao", mockCtlDocumentDao);
        injectDependency(manager, "nioFileManager", mockNioFileManager);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(manager, "providerInboxRoutingDao", mockProviderInboxRoutingDao);
        injectDependency(manager, "patientConsentManager", mockPatientConsentManager);
        injectDependency(manager, "providerLabRoutingDao", mockProviderLabRoutingDao);
        injectDependency(manager, "patientLabRoutingDao", mockPatientLabRoutingDao);
        injectDependency(manager, "queueDocumentLinkDAO", mockQueueDocumentLinkDao);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    private void grantEdocReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(true);
    }

    private void denyEdocReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), eq("")))
                .thenReturn(false);
    }

    private void grantEdocWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), eq("")))
                .thenReturn(true);
    }

    private void denyEdocWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), eq("")))
                .thenReturn(false);
    }

    private void grantEdocExecutePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("x"), eq("")))
                .thenReturn(true);
    }

    private void grantDocumentsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(true);
    }

    private void denyDocumentsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);
    }

    private void grantDocumentsWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);
    }

    private Document createTestDocument(Integer id, String filename) {
        Document doc = new Document();
        doc.setDocumentNo(id);
        doc.setDocfilename(filename);
        doc.setDocdesc("Test document");
        doc.setNumberofpages(1);
        return doc;
    }

    // -----------------------------------------------------------------------
    // getDocument
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocument")
    class GetDocument {

        @Test
        @DisplayName("should return document when valid ID and read privilege")
        void shouldReturnDocument_whenValidIdAndPrivilege() {
            grantEdocReadPrivilege();
            Document expected = createTestDocument(1, "test.pdf");
            when(mockDocumentDao.find(1)).thenReturn(expected);

            Document result = manager.getDocument(loggedInInfo, 1);

            assertThat(result).isSameAs(expected);
            verify(mockDocumentDao).find(1);
        }

        @Test
        @DisplayName("should return null when document not found")
        void shouldReturnNull_whenDocumentNotFound() {
            grantEdocReadPrivilege();
            when(mockDocumentDao.find(999)).thenReturn(null);

            Document result = manager.getDocument(loggedInInfo, 999);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied")
        void shouldThrowException_whenReadPrivilegeDenied() {
            denyEdocReadPrivilege();

            assertThatThrownBy(() -> manager.getDocument(loggedInInfo, 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Read Access Denied _edoc");
        }
    }

    // -----------------------------------------------------------------------
    // getDocumentsByDemographicNo
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocumentsByDemographicNo")
    class GetDocumentsByDemographicNo {

        @Test
        @DisplayName("should return documents for demographic number")
        void shouldReturnDocuments_whenDemographicNoProvided() {
            List<Document> expected = List.of(createTestDocument(1, "a.pdf"), createTestDocument(2, "b.pdf"));
            when(mockDocumentDao.findByDemographicId("123")).thenReturn(expected);

            List<Document> result = manager.getDocumentsByDemographicNo(loggedInInfo, 123);

            assertThat(result).hasSize(2);
            verify(mockDocumentDao).findByDemographicId("123");
        }

        @Test
        @DisplayName("should return null when dao returns null")
        void shouldReturnNull_whenDaoReturnsNull() {
            when(mockDocumentDao.findByDemographicId("999")).thenReturn(null);

            List<Document> result = manager.getDocumentsByDemographicNo(loggedInInfo, 999);

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // getCtlDocumentByDocumentId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getCtlDocumentByDocumentId")
    class GetCtlDocumentByDocumentId {

        @Test
        @DisplayName("should return ctl document when valid ID and privilege")
        void shouldReturnCtlDocument_whenValidIdAndPrivilege() {
            grantEdocReadPrivilege();
            CtlDocument expected = new CtlDocument();
            when(mockCtlDocumentDao.getCtrlDocument(42)).thenReturn(expected);

            CtlDocument result = manager.getCtlDocumentByDocumentId(loggedInInfo, 42);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied")
        void shouldThrowException_whenReadPrivilegeDenied() {
            denyEdocReadPrivilege();

            assertThatThrownBy(() -> manager.getCtlDocumentByDocumentId(loggedInInfo, 42))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Read Access Denied _edoc");
        }
    }

    // -----------------------------------------------------------------------
    // getDocumentsUpdateAfterDate
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocumentsUpdateAfterDate")
    class GetDocumentsUpdateAfterDate {

        @Test
        @DisplayName("should return documents updated after date")
        void shouldReturnDocuments_whenUpdatedAfterDate() {
            grantEdocReadPrivilege();
            Date cutoff = new Date();
            List<Document> expected = List.of(createTestDocument(1, "new.pdf"));
            when(mockDocumentDao.findByUpdateDate(cutoff, 10)).thenReturn(expected);

            List<Document> result = manager.getDocumentsUpdateAfterDate(loggedInInfo, cutoff, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied")
        void shouldThrowException_whenReadPrivilegeDenied() {
            denyEdocReadPrivilege();

            assertThatThrownBy(() -> manager.getDocumentsUpdateAfterDate(loggedInInfo, new Date(), 10))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getDocumentsByDemographicIdUpdateAfterDate (consent-gated)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocumentsByDemographicIdUpdateAfterDate")
    class GetDocumentsByDemographicIdUpdateAfterDate {

        @Test
        @DisplayName("should return documents when provider has consent")
        void shouldReturnDocuments_whenProviderHasConsent() {
            when(mockPatientConsentManager.hasProviderSpecificConsent(loggedInInfo)).thenReturn(true);
            Date cutoff = new Date();
            List<Document> expected = List.of(createTestDocument(1, "doc.pdf"));
            when(mockDocumentDao.findByDemographicUpdateAfterDate(100, cutoff)).thenReturn(expected);

            List<Document> result = manager.getDocumentsByDemographicIdUpdateAfterDate(loggedInInfo, 100, cutoff);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return documents when consent type not managed")
        void shouldReturnDocuments_whenConsentTypeNotManaged() {
            when(mockPatientConsentManager.hasProviderSpecificConsent(loggedInInfo)).thenReturn(false);
            when(mockPatientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER)).thenReturn(null);
            Date cutoff = new Date();
            List<Document> expected = List.of(createTestDocument(1, "doc.pdf"));
            when(mockDocumentDao.findByDemographicUpdateAfterDate(100, cutoff)).thenReturn(expected);

            List<Document> result = manager.getDocumentsByDemographicIdUpdateAfterDate(loggedInInfo, 100, cutoff);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no consent and consent type managed")
        void shouldReturnEmptyList_whenNoConsentAndConsentTypeManaged() {
            when(mockPatientConsentManager.hasProviderSpecificConsent(loggedInInfo)).thenReturn(false);
            when(mockPatientConsentManager.getConsentType(ConsentType.PROVIDER_CONSENT_FILTER)).thenReturn(new ConsentType());

            List<Document> result = manager.getDocumentsByDemographicIdUpdateAfterDate(loggedInInfo, 100, new Date());

            assertThat(result).isEmpty();
            verify(mockDocumentDao, never()).findByDemographicUpdateAfterDate(anyInt(), any(Date.class));
        }
    }

    // -----------------------------------------------------------------------
    // saveDocument(LoggedInInfo, Document, CtlDocument)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("saveDocument with Document and CtlDocument")
    class SaveDocumentWithCtlDocument {

        @Test
        @DisplayName("should persist new document when ID is null")
        void shouldPersistNewDocument_whenIdIsNull() {
            grantEdocWritePrivilege();
            Document doc = createTestDocument(null, "new.pdf");
            CtlDocument ctlDoc = new CtlDocument();
            ctlDoc.setId(new CtlDocumentPK());

            // simulate ID assignment during persist
            doAnswer(inv -> {
                Document d = inv.getArgument(0);
                d.setDocumentNo(42);
                return null;
            }).when(mockDocumentDao).persist(any(Document.class));

            Integer result = manager.saveDocument(loggedInInfo, doc, ctlDoc);

            assertThat(result).isEqualTo(42);
            verify(mockDocumentDao).persist(doc);
            verify(mockDocumentDao, never()).merge(any());
            verify(mockCtlDocumentDao).persist(ctlDoc);
        }

        @Test
        @DisplayName("should merge existing document when ID is positive")
        void shouldMergeDocument_whenIdIsPositive() {
            grantEdocWritePrivilege();
            Document doc = createTestDocument(10, "existing.pdf");
            CtlDocument ctlDoc = new CtlDocument();
            ctlDoc.setId(new CtlDocumentPK());

            Integer result = manager.saveDocument(loggedInInfo, doc, ctlDoc);

            assertThat(result).isEqualTo(10);
            verify(mockDocumentDao).merge(doc);
            verify(mockDocumentDao, never()).persist(any());
            verify(mockCtlDocumentDao).persist(ctlDoc);
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied")
        void shouldThrowException_whenWritePrivilegeDenied() {
            denyEdocWritePrivilege();
            Document doc = createTestDocument(1, "test.pdf");
            CtlDocument ctlDoc = new CtlDocument();

            assertThatThrownBy(() -> manager.saveDocument(loggedInInfo, doc, ctlDoc))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Write Access Denied _edoc");
        }
    }

    // -----------------------------------------------------------------------
    // getDocumentsByProgramProviderDemographicDate
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocumentsByProgramProviderDemographicDate")
    class GetDocumentsByProgramProviderDemographicDate {

        @Test
        @DisplayName("should return documents for program-provider-demographic-date criteria")
        void shouldReturnDocuments_whenCriteriaProvided() {
            grantEdocReadPrivilege();
            Calendar cal = Calendar.getInstance();
            List<Document> expected = List.of(createTestDocument(1, "doc.pdf"));
            when(mockDocumentDao.findByProgramProviderDemographicUpdateDate(
                    eq(5), eq("999998"), eq(100), any(Date.class), eq(20)))
                    .thenReturn(expected);

            List<Document> result = manager.getDocumentsByProgramProviderDemographicDate(
                    loggedInInfo, 5, "999998", 100, cal, 20);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw RuntimeException when read privilege denied")
        void shouldThrowException_whenReadPrivilegeDenied() {
            denyEdocReadPrivilege();

            assertThatThrownBy(() -> manager.getDocumentsByProgramProviderDemographicDate(
                    loggedInInfo, 5, "999998", 100, Calendar.getInstance(), 20))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getDemographicDocumentsByDocumentType
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDemographicDocumentsByDocumentType")
    class GetDemographicDocumentsByDocumentType {

        @Test
        @DisplayName("should return documents filtered by type and demographic")
        void shouldReturnDocuments_whenFilteredByType() {
            grantDocumentsReadPrivilege();
            List<Document> expected = List.of(createTestDocument(1, "lab.pdf"));
            when(mockDocumentDao.findByDemographicAndDoctype(100, DocumentDao.DocumentType.LAB))
                    .thenReturn(expected);

            List<Document> result = manager.getDemographicDocumentsByDocumentType(
                    loggedInInfo, 100, DocumentDao.DocumentType.LAB);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should throw RuntimeException when documents read privilege denied")
        void shouldThrowException_whenDocumentsReadDenied() {
            denyDocumentsReadPrivilege();

            assertThatThrownBy(() -> manager.getDemographicDocumentsByDocumentType(
                    loggedInInfo, 100, DocumentDao.DocumentType.LAB))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Access Denied");
        }
    }

    // -----------------------------------------------------------------------
    // getDocumentByDemographicAndFilename
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getDocumentByDemographicAndFilename")
    class GetDocumentByDemographicAndFilename {

        @Test
        @DisplayName("should return document matching demographic and filename")
        void shouldReturnDocument_whenMatchFound() {
            grantDocumentsReadPrivilege();
            Document expected = createTestDocument(1, "report.pdf");
            when(mockDocumentDao.findByDemographicAndFilename(100, "report.pdf")).thenReturn(expected);

            Document result = manager.getDocumentByDemographicAndFilename(loggedInInfo, 100, "report.pdf");

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should throw RuntimeException when privilege denied")
        void shouldThrowException_whenPrivilegeDenied() {
            denyDocumentsReadPrivilege();

            assertThatThrownBy(() -> manager.getDocumentByDemographicAndFilename(loggedInInfo, 100, "report.pdf"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // getProvidersThatHaveAcknowledgedDocument
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getProvidersThatHaveAcknowledgedDocument")
    class GetProvidersThatHaveAcknowledgedDocument {

        @Test
        @DisplayName("should return only acknowledged provider numbers")
        void shouldReturnOnlyAcknowledgedProviders() {
            ProviderInboxItem ackItem = mock(ProviderInboxItem.class);
            when(ackItem.getStatus()).thenReturn(ProviderInboxItem.ACK);
            when(ackItem.getProviderNo()).thenReturn("111");

            ProviderInboxItem pendingItem = mock(ProviderInboxItem.class);
            when(pendingItem.getStatus()).thenReturn("N");

            when(mockProviderInboxRoutingDao.getProvidersWithRoutingForDocument("DOC", 42))
                    .thenReturn(List.of(ackItem, pendingItem));

            List<String> result = manager.getProvidersThatHaveAcknowledgedDocument(loggedInInfo, 42);

            assertThat(result).containsExactly("111");
        }

        @Test
        @DisplayName("should return empty list when no providers acknowledged")
        void shouldReturnEmptyList_whenNoneAcknowledged() {
            ProviderInboxItem pendingItem = mock(ProviderInboxItem.class);
            when(pendingItem.getStatus()).thenReturn("N");

            when(mockProviderInboxRoutingDao.getProvidersWithRoutingForDocument("DOC", 42))
                    .thenReturn(List.of(pendingItem));

            List<String> result = manager.getProvidersThatHaveAcknowledgedDocument(loggedInInfo, 42);

            assertThat(result).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // addDocumentToQueue
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addDocumentToQueue")
    class AddDocumentToQueue {

        @Test
        @DisplayName("should add document to queue when valid queueId")
        void shouldAddToQueue_whenValidQueueId() {
            grantEdocWritePrivilege();

            Integer result = manager.addDocumentToQueue(loggedInInfo, 10, 5);

            assertThat(result).isEqualTo(5);
            verify(mockQueueDocumentLinkDao).addActiveQueueDocumentLink(5, 10);
        }

        @Test
        @DisplayName("should return null when queueId is null")
        void shouldReturnNull_whenQueueIdIsNull() {
            grantEdocWritePrivilege();

            Integer result = manager.addDocumentToQueue(loggedInInfo, 10, null);

            assertThat(result).isNull();
            verify(mockQueueDocumentLinkDao, never()).addActiveQueueDocumentLink(anyInt(), anyInt());
        }

        @Test
        @DisplayName("should return null when queueId is zero or negative")
        void shouldReturnNull_whenQueueIdIsZeroOrNegative() {
            grantEdocWritePrivilege();

            Integer result = manager.addDocumentToQueue(loggedInInfo, 10, 0);

            assertThat(result).isNull();
            verify(mockQueueDocumentLinkDao, never()).addActiveQueueDocumentLink(anyInt(), anyInt());
        }

        @Test
        @DisplayName("should throw RuntimeException when write privilege denied")
        void shouldThrowException_whenWritePrivilegeDenied() {
            denyEdocWritePrivilege();

            assertThatThrownBy(() -> manager.addDocumentToQueue(loggedInInfo, 10, 5))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Write Access Denied _edoc");
        }
    }

    // -----------------------------------------------------------------------
    // moveDocument
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("moveDocument")
    class MoveDocument {

        @Test
        @DisplayName("should throw RuntimeException when execute privilege denied")
        void shouldThrowException_whenExecutePrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("x"), eq("")))
                    .thenReturn(false);

            Document doc = createTestDocument(1, "test.pdf");

            assertThatThrownBy(() -> manager.moveDocument(loggedInInfo, doc, "/from", "/to"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Access Denied _edoc");
        }
    }

    // -----------------------------------------------------------------------
    // renderDocument (by documentId string)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("renderDocument security")
    class RenderDocumentSecurity {

        @Test
        @DisplayName("should throw RuntimeException when documents read privilege denied for render")
        void shouldThrowException_whenRenderPrivilegeDenied() {
            denyDocumentsReadPrivilege();

            assertThatThrownBy(() -> manager.renderDocument(loggedInInfo, "42"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Access Denied");
        }
    }
}
