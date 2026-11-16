/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocumentManagerImpl#createDocument} files documents for the REST API
 * (document/saveDocumentToDemographic, document/uploadPendingDocuments and consultation
 * attachments), whose description the caller chooses. document.docdesc holds 255 characters (code
 * points) and the packaged database cut a longer description without a word. Unmodified API
 * clients keep working: the description is shortened to the column, never inside a surrogate pair,
 * and the response the endpoints return carries the description as stored.
 */
@Tag("unit")
@DisplayName("DocumentManagerImpl document description")
class DocumentManagerImplDescriptionUnitTest extends CarlosUnitTestBase {

    private static final int MAX = Document.DESCRIPTION_MAX_LENGTH;

    private SecurityInfoManager securityInfoManager;
    private DocumentDao documentDao;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_edoc"), eq("w"), eq(""))).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Document>getArgument(0).setDocumentNo(321);
            return null;
        }).when(documentDao).persist(any(Document.class));
    }

    private Document create(String description, String fileName) throws Exception {
        try (MockedStatic<CarlosProperties> propertiesMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            propertiesMock.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(tempDir.toString());
            DocumentManagerImpl manager = new DocumentManagerImpl();
            injectDependency(manager, "securityInfoManager", securityInfoManager);
            injectDependency(manager, "documentDao", documentDao);
            injectDependency(manager, "ctlDocumentDao", mock(CtlDocumentDao.class));
            injectDependency(manager, "patientLabRoutingDao", mock(PatientLabRoutingDao.class));
            injectDependency(manager, "providerLabRoutingDao", mock(ProviderLabRoutingDao.class));
            Document document = new Document();
            document.setDocdesc(description);
            document.setDocfilename(fileName);
            return manager.createDocument(loggedInInfo, document, null, null, "body".getBytes(StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"d", "😀"})
    void shouldStoreTheDescriptionShortenedToTheColumn_whenTheApiCallerSendsALongerOne(String character) throws Exception {
        Document created = create(character.repeat(MAX + 30), "scan.pdf");

        ArgumentCaptor<Document> persisted = ArgumentCaptor.forClass(Document.class);
        verify(documentDao).persist(persisted.capture());
        assertThat(persisted.getValue().getDocdesc()).isEqualTo(character.repeat(MAX));
        assertThat(created.getDocdesc()).isEqualTo(character.repeat(MAX));
    }

    @Test
    void shouldStoreTheDescriptionUnchanged_whenItFitsTheColumn() throws Exception {
        String description = "😀".repeat(MAX);

        assertThat(create(description, "scan.pdf").getDocdesc()).isEqualTo(description);
    }

    @Test
    void shouldDescribeTheDocumentByItsStoredFileName_whenTheApiCallerSendsNoDescription() throws Exception {
        Document created = create("", "scan.pdf");

        assertThat(created.getDocdesc()).isEqualTo(created.getDocfilename()).matches("\\d{14}_\\d{5}_scan\\.pdf");
    }
}
