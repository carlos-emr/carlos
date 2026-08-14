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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EDocUtil")
@Tag("unit")
@Tag("documentManager")
class EDocUtilDeleteDocumentUnitTest extends CarlosUnitTestBase {

    private CtlDocumentDao ctlDocumentDao;
    private ConsultDocsDao consultDocsDao;
    private DemographicManager demographicManager;
    private DocumentDao documentDao;
    private EFormDocsDao eFormDocsDao;
    private LoggedInInfo loggedInInfo;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private ProgramManager2 programManager2;
    private ProviderDao providerDao;
    private String previousDocumentDir;
    private String previousFilterOnFacility;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        clearCachedDocumentDao();
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        previousFilterOnFacility = CarlosProperties.getInstance().getProperty("FILTER_ON_FACILITY");
        CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", "false");
        ctlDocumentDao = mock(CtlDocumentDao.class);
        consultDocsDao = mock(ConsultDocsDao.class);
        demographicManager = mock(DemographicManager.class);
        documentDao = mock(DocumentDao.class);
        eFormDocsDao = mock(EFormDocsDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        programManager2 = mock(ProgramManager2.class);
        providerDao = mock(ProviderDao.class);
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
        registerMock(ConsultDocsDao.class, consultDocsDao);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(DocumentDao.class, documentDao);
        registerMock(EFormDocsDao.class, eFormDocsDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);
        registerMock(ProgramManager2.class, programManager2);
        registerMock(ProviderDao.class, providerDao);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(programManager2.getProgramDomain(loggedInInfo, "999998")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
        if (previousFilterOnFacility == null) {
            CarlosProperties.getInstance().remove("FILTER_ON_FACILITY");
        } else {
            CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", previousFilterOnFacility);
        }
        clearCachedDocumentDao();
    }

    @Test
    @DisplayName("should soft-delete document when it is not an outbound email archive eDoc")
    void shouldSoftDeleteDocument_whenNotOutboundEmailArchiveEdoc() {
        Document document = activeDocument();
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(false);

        EDocUtil.deleteDocument("321");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_DELETED);
        assertThat(document.getUpdatedatetime()).isNotNull();
        verify(outboundEmailArchiveDao).existsByDocumentNo(321);
        verify(documentDao).merge(document);
    }

    @Test
    @DisplayName("should reject normal delete when document backs any outbound email archive")
    void shouldRejectDelete_whenDocumentBacksOutboundEmailArchive() {
        Document document = activeDocument();
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.deleteDocument("321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ACTIVE);
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should reject undelete when document backs an outbound email archive")
    void shouldRejectUndelete_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.undeleteDocument("321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(ctlDocumentDao);
        verify(documentDao, never()).find(any());
    }

    @Test
    @DisplayName("should reject refile when document backs an outbound email archive")
    void shouldRejectRefile_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.refileDocument("321", "1"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).find(any());
    }

    @Test
    @DisplayName("should reject edit when document backs an outbound email archive")
    void shouldRejectEdit_whenDocumentBacksOutboundEmailArchive() {
        EDoc editedDocument = new EDoc();
        editedDocument.setDocId("321");
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.editDocumentSQL(editedDocument, false))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).find(any());
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should reject edit when an ordinary document is assigned an outbound archive filename")
    void shouldRejectEdit_whenFileNameBelongsToOutboundEmailArchive() {
        EDoc editedDocument = new EDoc();
        editedDocument.setDocId("321");
        editedDocument.setFileName("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.editDocumentSQL(editedDocument, false))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).find(any());
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should reject document metadata creation when filename belongs to an outbound email archive")
    void shouldRejectAddDocumentSql_whenFilenameBelongsToOutboundEmailArchive() {
        EDoc newDocument = new EDoc();
        newDocument.setFileName("archive.pdf");
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.addDocumentSQL(newDocument))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).persist(any(Document.class));
    }

    @Test
    @DisplayName("should reject legacy document creation when filename belongs to an outbound email archive")
    void shouldRejectAddDocument_whenFilenameBelongsToOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.addDocument(
                "123",
                "archive.pdf",
                "Archive",
                "DOC",
                null,
                null,
                "application/pdf",
                null,
                null,
                null,
                "999998",
                "999998"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).persist(any(Document.class));
    }

    @Test
    @DisplayName("should reject document file writes when filename belongs to an outbound email archive")
    void shouldRejectWriteDocContent_whenFilenameBelongsToOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.writeDocContent("archive.pdf", new byte[] {1}))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should reject consult attachment when document backs an outbound email archive")
    void shouldRejectConsultAttachment_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.attachDocConsult("999998", "321", "456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(consultDocsDao, never()).persist(any(ConsultDocs.class));
    }

    @Test
    @DisplayName("should reject eForm attachment when document backs an outbound email archive")
    void shouldRejectEFormAttachment_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.attachDocEForm("999998", "321", "456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(eFormDocsDao, never()).persist(any(EFormDocs.class));
    }

    @Test
    @DisplayName("should reject consult detach when document backs an outbound email archive")
    void shouldRejectConsultDetach_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.detachDocConsult("321", "456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(consultDocsDao);
    }

    @Test
    @DisplayName("should reject eForm detach when document backs an outbound email archive")
    void shouldRejectEFormDetach_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.detachDocEForm("321", "456"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verifyNoInteractions(eFormDocsDao);
    }

    @Test
    @DisplayName("should reject page-count mutation when document backs an outbound email archive")
    void shouldRejectSubtractOnePage_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.subtractOnePage("321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).find(any());
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should reject tickler HTML reads when document backs an outbound email archive")
    void shouldRejectHtmlTicklers_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.getHtmlTicklers(loggedInInfo, "321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should reject acknowledgement HTML reads when document backs an outbound email archive")
    void shouldRejectHtmlAcknowledgement_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.getHtmlAcknowledgement(Locale.CANADA, "321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should reject annotation HTML reads when document backs an outbound email archive")
    void shouldRejectHtmlAnnotation_whenDocumentBacksOutboundEmailArchive() {
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.getHtmlAnnotation("321"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should hide last document description when newest document backs an outbound email archive")
    void shouldHideLastDocumentDesc_whenNewestDocumentBacksOutboundEmailArchive() {
        Document document = activeDocument();
        document.setDocdesc("Outbound email archive 123");
        when(documentDao.findMaxDocNo()).thenReturn(321);
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);

        assertThat(EDocUtil.getLastDocumentDesc()).isNull();
    }

    @Test
    @DisplayName("should reject document directory reads when filename belongs to an outbound email archive")
    void shouldRejectReadContent_whenFilenameBelongsToOutboundEmailArchive() throws Exception {
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());
        Files.write(tempDir.resolve("archive.pdf"), new byte[] {1});
        when(outboundEmailArchiveDao.existsByFileName("archive.pdf")).thenReturn(true);

        assertThatThrownBy(() -> EDocUtil.readContent("archive.pdf"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
    }

    @Test
    @DisplayName("should omit outbound archive eDocs from demographic export document lists")
    void shouldOmitOutboundArchiveDocuments_whenListingDemographicDocumentsForExport() {
        Document archiveDocument = activeDocument();
        Document ordinaryDocument = activeDocument(654);
        when(documentDao.findConstultDocsDocsAndProvidersByModule(DocumentDao.Module.DEMOGRAPHIC, 123))
                .thenReturn(List.of(new Object[] {archiveDocument}, new Object[] {ordinaryDocument}));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        List<EDoc> documents = EDocUtil.listDemoDocs(loggedInInfo, "123");

        assertThat(documents)
                .extracting(EDoc::getDocId)
                .containsExactly("654");
    }

    @Test
    @DisplayName("should omit outbound archive eDocs from general demographic document lists")
    void shouldOmitOutboundArchiveDocuments_whenListingDemographicDocuments() {
        Document archiveDocument = activeDocument();
        Document ordinaryDocument = activeDocument(654);
        when(documentDao.findDocuments("demographic", "123", null, false, false, true, EDocUtil.EDocSort.OBSERVATIONDATE, null))
                .thenReturn(List.of(new Object[] {new CtlDocument(), archiveDocument}, new Object[] {new CtlDocument(), ordinaryDocument}));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        List<EDoc> documents = EDocUtil.listDocs(
                loggedInInfo,
                "demographic",
                "123",
                null,
                EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE);

        assertThat(documents)
                .extracting(EDoc::getDocId)
                .containsExactly("654");
    }

    @Test
    @DisplayName("should omit outbound archive eDocs from updated demographic document lists")
    void shouldOmitOutboundArchiveDocuments_whenListingUpdatedDemographicDocuments() {
        Date since = new Date();
        Document archiveDocument = activeDocument();
        Document ordinaryDocument = activeDocument(654);
        when(documentDao.findByDemographicUpdateDate(123, since))
                .thenReturn(List.of(archiveDocument, ordinaryDocument));
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321, 654))).thenReturn(Set.of(321));

        List<EDoc> documents = EDocUtil.listAllDemographicDocsSince(loggedInInfo, 123, since);

        assertThat(documents)
                .extracting(EDoc::getDocId)
                .containsExactly("654");
    }

    @Test
    @DisplayName("should not query outbound archive metadata when document does not exist")
    void shouldNotQueryOutboundArchiveMetadata_whenDocumentDoesNotExist() {
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(null);

        EDocUtil.deleteDocument("321");

        verifyNoInteractions(outboundEmailArchiveDao);
        verify(documentDao, never()).merge(any(Document.class));
    }

    @Test
    @DisplayName("should restore active status when undelete ctl status is empty")
    void shouldRestoreActiveStatus_whenUndeleteCtlStatusIsEmpty() {
        Document document = deletedDocument();
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setStatus("");
        when(ctlDocumentDao.getCtrlDocument(321)).thenReturn(ctlDocument);
        when(documentDao.find((Object) Integer.valueOf(321))).thenReturn(document);

        EDocUtil.undeleteDocument("321");

        assertThat(document.getStatus()).isEqualTo(Document.STATUS_ACTIVE);
        assertThat(document.getUpdatedatetime()).isNotNull();
        verify(documentDao).merge(document);
    }

    @Test
    @DisplayName("should format provider name when one name part is missing")
    void shouldFormatProviderName_whenOneNamePartIsMissing() {
        Provider provider = new Provider();
        provider.setFirstName("Alex");
        provider.setLastName(null);
        when(providerDao.getProvider("12")).thenReturn(provider);

        String name = EDocUtil.getProviderName("12");

        assertThat(name).isEqualTo("ALEX");
    }

    @Test
    @DisplayName("should include alias when demographic name parts are missing")
    void shouldIncludeAlias_whenDemographicNamePartsAreMissing() {
        Demographic demographic = new Demographic(123);
        demographic.setFirstName(null);
        demographic.setLastName(null);
        demographic.setAlias("Lex");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);

        String name = EDocUtil.getDemographicName(loggedInInfo, "123");

        assertThat(name).isEqualTo("(Lex)");
    }

    @Test
    @DisplayName("should return N/A when demographic name and alias are missing")
    void shouldReturnNotAvailable_whenDemographicNameAndAliasAreMissing() {
        Demographic demographic = new Demographic(123);
        demographic.setFirstName(" ");
        demographic.setLastName(null);
        demographic.setAlias(" ");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);

        String name = EDocUtil.getDemographicName(loggedInInfo, "123");

        assertThat(name).isEqualTo("N/A");
    }

    private Document activeDocument() {
        return activeDocument(321);
    }

    private Document activeDocument(Integer documentNo) {
        Document document = new Document();
        document.setDocumentNo(documentNo);
        document.setStatus(Document.STATUS_ACTIVE);
        return document;
    }

    private Document deletedDocument() {
        Document document = new Document();
        document.setDocumentNo(321);
        document.setStatus(Document.STATUS_DELETED);
        return document;
    }

    private void clearCachedDocumentDao() throws Exception {
        Field documentDaoField = EDocUtil.class.getDeclaredField("documentDao");
        documentDaoField.setAccessible(true);
        documentDaoField.set(null, null);
        Field consultDocsDaoField = EDocUtil.class.getDeclaredField("consultDocsDao");
        consultDocsDaoField.setAccessible(true);
        consultDocsDaoField.set(null, null);
        Field eformDocsDaoField = EDocUtil.class.getDeclaredField("eformDocsDao");
        eformDocsDaoField.setAccessible(true);
        eformDocsDaoField.set(null, null);
    }
}
