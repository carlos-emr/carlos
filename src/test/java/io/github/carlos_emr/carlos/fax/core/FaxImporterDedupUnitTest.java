/*
 * Copyright (c) 2026. CARLOS EMR contributors and others.
 *
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
 */
package io.github.carlos_emr.carlos.fax.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openpdf.text.Document;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Unit tests for {@link FaxImporter} duplicate-import prevention and failed-import
 * filename persistence.
 *
 * <p>Duplicate prevention is keyed on the provider job id (SRFax FaxDetailsID): when
 * mark-as-read failed on a previous cycle the fax stays in the provider's UNREAD pull,
 * and without the dedup guard {@code generateUniqueFilename()} would file it as a brand
 * new document. {@link FaxImporter#poll()} must skip the download entirely for a job id
 * whose prior persisted rows prove the document already reached the EMR, and only retry
 * clearing the unread flag on the provider.</p>
 *
 * <p>Follows the setup pattern of {@link FaxImporterPollTest}: the importer is built via
 * the package-visible test-seam constructor pointing at scratch directories, and
 * {@code initialize()} is invoked explicitly (in production it is {@code @PostConstruct}).</p>
 *
 * @since 2026-08-21
 * @see FaxImporter#isAlreadyImported(List, String)
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxImporter Duplicate-Import Prevention Tests")
class FaxImporterDedupUnitTest extends CarlosUnitTestBase {

    private static final Long PROVIDER_JOB_ID = 777L;
    private static final String ACCOUNT_FAX_NUMBER = "5551230000";

    private FaxConfigDao faxConfigDao;
    private FaxJobDao faxJobDao;
    private QueueDocumentLinkDao queueDocumentLinkDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private FaxProviderClientFactory faxProviderClientFactory;
    private FaxProviderClient faxProviderClient;

    private FaxImporter faxImporter;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path faxIncomingDir;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path documentDir;

    private String originalFaxIncomingDir;

    @BeforeEach
    void setUp() throws FaxProviderException {
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        faxProviderClientFactory = mock(FaxProviderClientFactory.class);
        faxProviderClient = mock(FaxProviderClient.class);

        // poll() is a no-op unless initialize() has run (it is @PostConstruct in production).
        // Point the importer at scratch directories and initialize it for real.
        CarlosProperties properties = CarlosProperties.getInstance();
        originalFaxIncomingDir = properties.getProperty("FAX_INCOMING_DIR");
        properties.setProperty("FAX_INCOMING_DIR", faxIncomingDir.toString());

        faxImporter = new FaxImporter(faxConfigDao, faxJobDao, queueDocumentLinkDao,
                providerLabRoutingDao, faxProviderClientFactory, documentDir.toString());
        faxImporter.initialize();
        assertThat(faxImporter.isInitialized())
                .describedAs("poll() is a no-op unless the importer initialized; without this every "
                        + "assertion below would hold vacuously")
                .isTrue();

        when(faxProviderClientFactory.getClient(any())).thenReturn(faxProviderClient);
    }

    @AfterEach
    void restoreProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (originalFaxIncomingDir == null) {
            properties.setProperty("FAX_INCOMING_DIR", "");
        } else {
            properties.setProperty("FAX_INCOMING_DIR", originalFaxIncomingDir);
        }
    }

    @Test
    @DisplayName("should skip download and retry mark-as-read when fax already imported")
    void shouldSkipDownloadAndRetryMarkAsRead_whenFaxAlreadyImported() throws FaxProviderException {
        // Given: an inbound fax whose provider job id already has a RECEIVED (imported) row
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID))
                .thenReturn(Collections.singletonList(priorRow(FaxJob.STATUS.RECEIVED, null)));

        // When
        faxImporter.poll();

        // Then: no re-download, no new row; the provider acknowledgement is retried both ways
        // (mark-as-read for SRFax, delete for the middleware relay - each is the other's no-op)
        verify(faxProviderClient, never()).downloadFax(any(), any());
        verify(faxProviderClient).markFaxAsRead(config, inboundFax);
        verify(faxProviderClient).deleteFax(config, inboundFax);
        verify(faxJobDao, never()).persist(any());
    }

    @Test
    @DisplayName("should skip download when prior row shows imported-but-routing-failed (uppercase)")
    void shouldSkipDownload_whenPriorRowShowsImportedButRoutingFailed() throws FaxProviderException {
        // Given: post-import routing failure left an ERROR row whose statusString starts "IMPORTED..."
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.singletonList(
                priorRow(FaxJob.STATUS.ERROR, "IMPORTED BUT ROUTING FAILED - NEEDS MANUAL ASSIGNMENT")));

        // When
        faxImporter.poll();

        // Then: document already reached the EMR - skip download, retry mark-as-read only
        verify(faxProviderClient, never()).downloadFax(any(), any());
        verify(faxProviderClient).markFaxAsRead(config, inboundFax);
        verify(faxJobDao, never()).persist(any());
    }

    @Test
    @DisplayName("should skip download when prior row shows imported-but-routing-failed (mixed case)")
    void shouldSkipDownload_whenPriorRowShowsImportedMixedCaseVariant() throws FaxProviderException {
        // Given: the NumberFormatException routing branch writes a mixed-case "Imported..." string
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.singletonList(
                priorRow(FaxJob.STATUS.ERROR, "Imported but routing failed - manual assignment required")));

        // When
        faxImporter.poll();

        // Then
        verify(faxProviderClient, never()).downloadFax(any(), any());
        verify(faxProviderClient).markFaxAsRead(config, inboundFax);
        verify(faxJobDao, never()).persist(any());
    }

    @Test
    @DisplayName("should still download when prior row is a pre-import error")
    void shouldStillDownload_whenPriorRowIsPreImportError() throws FaxProviderException {
        // Given: a prior ERROR row from BEFORE the document reached the EMR - re-download is correct
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.singletonList(
                priorRow(FaxJob.STATUS.ERROR, "Download failed: x")));
        when(faxProviderClient.downloadFax(config, inboundFax))
                .thenThrow(new FaxProviderException("transient network error"));

        // When
        faxImporter.poll();

        // Then: the dedup guard let the retry through
        verify(faxProviderClient).downloadFax(config, inboundFax);
    }

    @Test
    @DisplayName("should still download when no prior rows exist for the job id")
    void shouldStillDownload_whenNoPriorRows() throws FaxProviderException {
        // Given: first sighting of this provider job id (DAO returns an empty list)
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.emptyList());
        when(faxProviderClient.downloadFax(config, inboundFax))
                .thenThrow(new FaxProviderException("transient network error"));

        // When
        faxImporter.poll();

        // Then
        verify(faxProviderClient).downloadFax(config, inboundFax);
    }

    @Test
    @DisplayName("should still download without a dedup lookup when job id is null")
    void shouldStillDownload_whenJobIdIsNull() throws FaxProviderException {
        // Given: a provider that reports no job id - dedup cannot key on anything
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(null);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxProviderClient.downloadFax(config, inboundFax))
                .thenThrow(new FaxProviderException("transient network error"));

        // When
        faxImporter.poll();

        // Then: no dedup lookup, download proceeds
        verify(faxJobDao, never()).findByProviderJobId(anyLong());
        verify(faxProviderClient).downloadFax(config, inboundFax);
    }

    @Test
    @DisplayName("should persist quarantined filename when import fails")
    void shouldPersistQuarantinedFilename_whenImportFails() throws Exception {
        // Given: download and quarantine succeed but the EMR document record cannot be created
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.emptyList());

        FaxJob downloadedFax = new FaxJob();
        downloadedFax.setDocument(Base64.getEncoder().encodeToString(createValidPdfBytes()));
        when(faxProviderClient.downloadFax(config, inboundFax)).thenReturn(downloadedFax);

        // EDocUtil.addDocumentSQL returning null drives importFromIncoming's failure path
        // (document record creation failed -> file moved back to incoming -> null EDoc).
        try (MockedStatic<EDocUtil> eDocUtilMock = Mockito.mockStatic(EDocUtil.class)) {
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any())).thenReturn(null);

            // When
            faxImporter.poll();
        }

        // Then: the persisted row carries the quarantined incoming file name, never null
        ArgumentCaptor<FaxJob> persisted = ArgumentCaptor.forClass(FaxJob.class);
        verify(faxJobDao).persist(persisted.capture());
        assertThat(persisted.getValue().getFile_name())
                .as("failed import must keep a usable file reference instead of a null filename")
                .isNotNull()
                .endsWith(".pdf");
        assertThat(persisted.getValue().getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
    }

    @Test
    @DisplayName("should move the file back to incoming when the document persist throws")
    void shouldMoveFileBackToIncoming_whenDocumentPersistThrows() throws Exception {
        // Given: download and quarantine succeed but the document persist throws unchecked
        // (the DAO path throws PersistenceException rather than returning null). The file has
        // already been moved into DOCUMENT_DIR at that point; without compensation it would be
        // stranded there - invisible to retryPendingImports and, mark-as-read having run,
        // never re-downloaded either.
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.emptyList());

        FaxJob downloadedFax = new FaxJob();
        downloadedFax.setDocument(Base64.getEncoder().encodeToString(createValidPdfBytes()));
        when(faxProviderClient.downloadFax(config, inboundFax)).thenReturn(downloadedFax);

        try (MockedStatic<EDocUtil> eDocUtilMock = Mockito.mockStatic(EDocUtil.class)) {
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any()))
                    .thenThrow(new RuntimeException("simulated persistence failure"));

            // When
            faxImporter.poll();
        }

        // Then: an ERROR row marks the fax pending retry, and the PDF is back under the
        // incoming directory (config subdirectory) where retryPendingImports scans.
        ArgumentCaptor<FaxJob> persisted = ArgumentCaptor.forClass(FaxJob.class);
        verify(faxJobDao).persist(persisted.capture());
        assertThat(persisted.getValue().getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(persisted.getValue().getStatusString())
                .startsWith("Downloaded but import failed");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(faxIncomingDir)) {
            assertThat(files.filter(f -> f.toString().endsWith(".pdf")).count())
                    .as("the quarantined PDF must be back in the incoming directory for retry")
                    .isEqualTo(1);
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(documentDir)) {
            assertThat(files.filter(f -> f.toString().endsWith(".pdf")).count())
                    .as("no orphan file may remain in DOCUMENT_DIR after the failed persist")
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("should store the file under the EDoc's DMS-prefixed filename on successful import")
    void shouldStoreFileUnderEdocFilename_onSuccessfulImport() throws Exception {
        // Given: a downloadable inbound fax and a document record that persists successfully
        FaxConfig config = createActiveConfig();
        FaxJob inboundFax = createInboundFax(PROVIDER_JOB_ID);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        when(faxProviderClient.listInboundFaxes(config)).thenReturn(Collections.singletonList(inboundFax));
        when(faxJobDao.findByProviderJobId(PROVIDER_JOB_ID)).thenReturn(Collections.emptyList());

        FaxJob downloadedFax = new FaxJob();
        downloadedFax.setDocument(Base64.getEncoder().encodeToString(createValidPdfBytes()));
        when(faxProviderClient.downloadFax(config, inboundFax)).thenReturn(downloadedFax);

        java.util.concurrent.atomic.AtomicReference<String> edocFileName = new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<EDocUtil> eDocUtilMock = Mockito.mockStatic(EDocUtil.class)) {
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any())).thenAnswer(inv -> {
                io.github.carlos_emr.carlos.documentManager.EDoc doc = inv.getArgument(0);
                edocFileName.set(doc.getFileName());
                return "4242";
            });

            // When
            faxImporter.poll();
        }

        // Then: the physical file lives under the EXACT name recorded on the document row
        // (EDoc prepends the DMS yyyyMMddHHmmss prefix; storing the file unprefixed left every
        // imported fax unopenable in the document viewer).
        assertThat(edocFileName.get()).isNotNull().matches("\\d{14}.+\\.pdf");
        assertThat(documentDir.resolve(edocFileName.get()))
                .as("file must be stored under the document row's filename")
                .exists();

        // And the persisted fax row references the same final name and is stamped inbound
        // (direction drives both the queue view's type label and the dedup scoping)
        ArgumentCaptor<FaxJob> persisted = ArgumentCaptor.forClass(FaxJob.class);
        verify(faxJobDao).persist(persisted.capture());
        assertThat(persisted.getValue().getFile_name()).isEqualTo(edocFileName.get());
        assertThat(persisted.getValue().getDirection()).isEqualTo(FaxJob.Direction.IN);
    }

    /**
     * Direct contract tests for {@link FaxImporter#isAlreadyImported(List, String)}.
     */
    @Nested
    @DisplayName("isAlreadyImported() contract")
    class IsAlreadyImportedTests {

        @Test
        @DisplayName("should return false for null prior rows")
        void shouldReturnFalse_forNullPriorRows() {
            assertThat(faxImporter.isAlreadyImported(null, ACCOUNT_FAX_NUMBER)).isFalse();
        }

        @Test
        @DisplayName("should return false for empty prior rows")
        void shouldReturnFalse_forEmptyPriorRows() {
            assertThat(faxImporter.isAlreadyImported(Collections.emptyList(), ACCOUNT_FAX_NUMBER)).isFalse();
        }

        @Test
        @DisplayName("should return false when only an outbound row shares the provider job id")
        void shouldReturnFalse_whenOnlyOutboundRowSharesJobId() {
            // Provider job ids from different sources share the jobId column; an OUT row that
            // merely collides on the number must not suppress importing a new inbound fax.
            FaxJob outbound = priorRow(FaxJob.STATUS.RECEIVED, "Imported");
            outbound.setDirection(FaxJob.Direction.OUT);
            FaxJob outboundComplete = priorRow(FaxJob.STATUS.COMPLETE, "Sent");
            outboundComplete.setDirection(FaxJob.Direction.OUT);
            assertThat(faxImporter.isAlreadyImported(java.util.Arrays.asList(outbound, outboundComplete), ACCOUNT_FAX_NUMBER)).isFalse();
        }

        @Test
        @DisplayName("should return true for a legacy inbound row with a null direction")
        void shouldReturnTrue_forLegacyRowWithNullDirection() {
            // Pre-direction rows (before the V1.0.15 backfill ran) must keep deduplicating.
            FaxJob legacy = priorRow(FaxJob.STATUS.RECEIVED, null);
            legacy.setDirection(null);
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(legacy), ACCOUNT_FAX_NUMBER)).isTrue();
        }

        @Test
        @DisplayName("should return true when a prior row is RECEIVED")
        void shouldReturnTrue_whenPriorRowIsReceived() {
            List<FaxJob> rows = Collections.singletonList(priorRow(FaxJob.STATUS.RECEIVED, null));
            assertThat(faxImporter.isAlreadyImported(rows, ACCOUNT_FAX_NUMBER)).isTrue();
        }

        @Test
        @DisplayName("should return true when a prior RECEIVED row is for THIS account's fax line")
        void shouldReturnTrue_whenPriorRowMatchesThisAccountFaxLine() {
            FaxJob prior = priorRow(FaxJob.STATUS.RECEIVED, null);
            prior.setFax_line(ACCOUNT_FAX_NUMBER);
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(prior), ACCOUNT_FAX_NUMBER)).isTrue();
        }

        @Test
        @DisplayName("should return true when a prior row's 11-digit fax line matches this 10-digit account (normalized)")
        void shouldReturnTrue_whenPriorFaxLineIs11DigitFormOfThisAccount() {
            FaxJob prior = priorRow(FaxJob.STATUS.RECEIVED, null);
            prior.setFax_line("1" + ACCOUNT_FAX_NUMBER); // provider-supplied 11-digit form
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(prior), ACCOUNT_FAX_NUMBER)).isTrue();
        }

        @Test
        @DisplayName("should return false for a stamped other-account row when THIS account has no fax line")
        void shouldReturnFalse_whenAccountFaxLineBlankAndPriorRowIsStamped() {
            FaxJob otherAccount = priorRow(FaxJob.STATUS.RECEIVED, null);
            otherAccount.setFax_line("5559990000");
            // Blank account line can't confirm ownership of a stamped row -> not ours.
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(otherAccount), "")).isFalse();
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(otherAccount), null)).isFalse();
        }

        @Test
        @DisplayName("should return true for a legacy blank-fax-line row even when THIS account has no fax line")
        void shouldReturnTrue_whenAccountFaxLineBlankAndPriorRowLegacyBlank() {
            FaxJob legacy = priorRow(FaxJob.STATUS.RECEIVED, null);
            legacy.setFax_line(null);
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(legacy), "")).isTrue();
        }

        @Test
        @DisplayName("should return false when the only prior row belongs to a DIFFERENT account (same numeric job id)")
        void shouldReturnFalse_whenPriorRowIsForADifferentAccount() {
            // Two accounts/backends can reuse the same numeric provider job id; a row imported by
            // another account must not suppress importing this account's genuinely new fax.
            FaxJob otherAccount = priorRow(FaxJob.STATUS.RECEIVED, null);
            otherAccount.setFax_line("5559990000");
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(otherAccount), ACCOUNT_FAX_NUMBER)).isFalse();
        }

        @Test
        @DisplayName("should return false when the prior row's fax line is short/malformed (< 10 digits)")
        void shouldReturnFalse_whenPriorFaxLineIsShortMalformed() {
            // A prior row carrying a truncated/garbage fax_line has no usable account key; it must
            // not be treated as a match against this account (else a genuine new fax is suppressed),
            // and a short value must never coincidentally match another short value.
            FaxJob shortLine = priorRow(FaxJob.STATUS.RECEIVED, null);
            shortLine.setFax_line("8476");
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(shortLine), ACCOUNT_FAX_NUMBER)).isFalse();
        }

        @Test
        @DisplayName("should return true for a legacy row with a blank fax line (pre-stamping upgrade)")
        void shouldReturnTrue_forLegacyRowWithBlankFaxLine() {
            // Rows imported before this release have no fax_line; an upgrade must still treat them
            // as already-held so an unacknowledged fax is not downloaded and imported again.
            FaxJob legacyNoLine = priorRow(FaxJob.STATUS.RECEIVED, null);
            legacyNoLine.setFax_line(null);
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(legacyNoLine), ACCOUNT_FAX_NUMBER)).isTrue();
        }

        @Test
        @DisplayName("should return true when status string starts with imported in any case")
        void shouldReturnTrue_whenStatusStringStartsWithImportedAnyCase() {
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "IMPORTED BUT ROUTING FAILED - NEEDS MANUAL ASSIGNMENT")), ACCOUNT_FAX_NUMBER))
                    .isTrue();
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "Imported but routing failed - manual assignment required")), ACCOUNT_FAX_NUMBER))
                    .isTrue();
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "imported on retry but routing failed")), ACCOUNT_FAX_NUMBER))
                    .isTrue();
        }

        @Test
        @DisplayName("should return true when the file is quarantined pending the import retry path")
        void shouldReturnTrue_whenFileQuarantinedPendingRetry() {
            // The quarantined file is owned by retryPendingImports, whose retry row carries no
            // provider job id — re-downloading here would duplicate the document post-retry.
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "Downloaded but import failed - pending retry from incoming directory")), ACCOUNT_FAX_NUMBER))
                    .isTrue();
        }

        @Test
        @DisplayName("should return false for other error status strings")
        void shouldReturnFalse_forOtherErrorStatusStrings() {
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "Download failed: timeout")), ACCOUNT_FAX_NUMBER)).isFalse();
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, "Download or save to incoming directory failed")), ACCOUNT_FAX_NUMBER))
                    .isFalse();
            assertThat(faxImporter.isAlreadyImported(Collections.singletonList(
                    priorRow(FaxJob.STATUS.ERROR, null)), ACCOUNT_FAX_NUMBER)).isFalse();
        }
    }

    // -- helper methods --

    private FaxConfig createActiveConfig() {
        FaxConfig config = new FaxConfig();
        config.setId(1);
        config.setActive(true);
        config.setDownload(true);
        config.setFaxUser("test-access-id");
        config.setFaxNumber(ACCOUNT_FAX_NUMBER);
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setQueue(1);
        return config;
    }

    private FaxJob createInboundFax(Long jobId) {
        FaxJob fax = new FaxJob();
        fax.setJobId(jobId);
        fax.setFile_name("inbound-referral.pdf");
        fax.setStamp(new Date());
        fax.setDirection(FaxJob.Direction.IN);
        return fax;
    }

    private FaxJob priorRow(FaxJob.STATUS status, String statusString) {
        FaxJob prior = new FaxJob();
        prior.setJobId(PROVIDER_JOB_ID);
        prior.setStatus(status);
        prior.setStatusString(statusString);
        return prior;
    }

    /**
     * Creates a minimal single-page PDF (via OpenPDF, same technique as
     * {@link FaxImporterCriticalGapsTest}) so saveToIncoming's validation passes.
     */
    private byte[] createValidPdfBytes() throws Exception {
        File tempFile = File.createTempFile("test-dedup-fax-", ".pdf");
        try {
            Document document = new Document(PageSize.LETTER);
            PdfWriter.getInstance(document, Files.newOutputStream(tempFile.toPath()));
            document.open();
            document.add(new Paragraph("Test fax page"));
            document.close();
            return Files.readAllBytes(tempFile.toPath());
        } finally {
            tempFile.delete();
        }
    }
}
