/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.fax.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the outcome when an inbound fax is imported but cannot be linked to its work queue.
 *
 * <p>{@code QueueDocumentLinkDaoImpl.addActiveQueueDocumentLink} now propagates persist failures
 * rather than swallowing them — a swallowed failure left the clinical document outside its queue
 * while reporting success. But by the time it is called the file has already left the incoming
 * directory and the document row is committed, so an escaping exception would skip the move-back
 * compensation and abort the sweep for every remaining fax. The document must be retained and the
 * partial import recorded instead.</p>
 *
 * @since 2026-07-25
 */
@DisplayName("FaxImporter queue-link failure")
@Tag("unit")
@Tag("fax")
class FaxImporterQueueLinkFailureUnitTest {

    @TempDir
    Path documentDir;
    @TempDir
    Path incomingDir;

    private QueueDocumentLinkDao queueDocumentLinkDao;
    private FaxImporter faxImporter;
    private Method importFromIncoming;

    @BeforeEach
    void setUp() throws Exception {
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        faxImporter = new FaxImporter(
                mock(FaxConfigDao.class),
                mock(FaxJobDao.class),
                queueDocumentLinkDao,
                mock(ProviderLabRoutingDao.class),
                mock(FaxProviderClientFactory.class),
                documentDir.toString());

        // faxIncomingDir is assigned by @PostConstruct initialize(), which we deliberately skip here
        // so the test does not depend on CarlosProperties configuration.
        java.lang.reflect.Field incoming = FaxImporter.class.getDeclaredField("faxIncomingDir");
        incoming.setAccessible(true);
        incoming.set(faxImporter, incomingDir);

        importFromIncoming = FaxImporter.class.getDeclaredMethod(
                "importFromIncoming", Path.class, FaxConfig.class, FaxJob.class);
        importFromIncoming.setAccessible(true);
    }

    @AfterEach
    void tearDown() {
        // @TempDir cleans both roots.
    }

    @Test
    @DisplayName("should keep the imported document and flag it when the queue link fails")
    void shouldKeepImportedDocument_whenQueueLinkFails() throws Exception {
        Path pending = incomingDir.resolve("incoming-referral.pdf");
        Files.copy(createValidPdf().toPath(), pending);

        FaxJob receivedFax = new FaxJob();
        receivedFax.setId(1);
        receivedFax.setFile_name("incoming-referral.pdf");
        receivedFax.setStamp(new Date());
        receivedFax.setStatus(FaxJob.STATUS.WAITING);

        doThrow(new RuntimeException("queue_document_link insert failed"))
                .when(queueDocumentLinkDao).addActiveQueueDocumentLink(anyInt(), anyInt());

        Object result;
        try (MockedStatic<EDocUtil> edocUtil = mockStatic(EDocUtil.class)) {
            edocUtil.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("4321");

            result = importFromIncoming.invoke(faxImporter, pending, createFaxConfig(), receivedFax);
        }

        // The document is retained: an imported-but-unqueued fax is recoverable by an operator,
        // a discarded one is not. Returning null here would have made the caller treat the whole
        // import as failed while the committed document row and moved file remained.
        assertThat(result).as("the imported document must be retained").isNotNull();
        assertThat(receivedFax.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(receivedFax.getStatusString()).contains("IMPORTED BUT NOT QUEUED");
    }

    private FaxConfig createFaxConfig() {
        FaxConfig config = new FaxConfig();
        config.setId(1);
        config.setActive(true);
        config.setDownload(true);
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setQueue(1);
        return config;
    }

    private File createValidPdf() throws IOException, DocumentException {
        File tempFile = File.createTempFile("test-queue-link-fax-", ".pdf");
        Document document = new Document(PageSize.LETTER);
        PdfWriter.getInstance(document, Files.newOutputStream(tempFile.toPath()));
        document.open();
        document.add(new Paragraph("Referral"));
        document.close();
        tempFile.deleteOnExit();
        return tempFile;
    }
}
