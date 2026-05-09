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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link FaxImporter#poll()} orchestration logic.
 *
 * <p>These tests focus on the poll() method's control flow: skipping inactive/download-disabled
 * configs, continuing after provider errors, and handling empty config lists. The deeper
 * import pipeline (saveAndInsertIntoQueue, PDF validation, etc.) is tested separately in
 * {@link FaxImporterCriticalGapsTest}.</p>
 *
 * <p><strong>Note:</strong> FaxImporter has static fields (DOCUMENT_DIR, FAX_TEMP_DIR) that
 * are initialized at class load time via CarlosProperties. This class loads successfully in
 * the test environment because CarlosProperties is initialized from the test config.</p>
 *
 * @since 2026-02-13
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxImporter poll() Orchestration Tests")
class FaxImporterPollTest extends CarlosUnitTestBase {

    private FaxConfigDao faxConfigDao;
    private FaxJobDao faxJobDao;
    private QueueDocumentLinkDao queueDocumentLinkDao;
    private ProviderLabRoutingDao providerLabRoutingDao;
    private FaxProviderClientFactory faxProviderClientFactory;
    private FaxProviderClient faxProviderClient;

    private FaxImporter faxImporter;

    @BeforeEach
    void setUp() {
        faxConfigDao = mock(FaxConfigDao.class);
        faxJobDao = mock(FaxJobDao.class);
        queueDocumentLinkDao = mock(QueueDocumentLinkDao.class);
        providerLabRoutingDao = mock(ProviderLabRoutingDao.class);
        faxProviderClientFactory = mock(FaxProviderClientFactory.class);
        faxProviderClient = mock(FaxProviderClient.class);

        faxImporter = new FaxImporter(faxConfigDao, faxJobDao, queueDocumentLinkDao,
                providerLabRoutingDao, faxProviderClientFactory);
        // poll() returns early when initialized=false. The @PostConstruct initialize() requires
        // a class-load-time DOCUMENT_DIR plus a writable incoming directory and can't be invoked
        // under unit-test isolation, so use the package-private test seam to skip directly to
        // the orchestration logic these tests target. faxIncomingDir stays null, which
        // retryPendingImports tolerates (short-circuits on null) — the tests don't cover retry.
        faxImporter.markInitializedForTest();
    }

    @Test
    @DisplayName("should skip inactive configs when polling")
    void shouldSkipInactiveConfigs_whenPolling() throws FaxProviderException {
        // Given: Two configs, both inactive (active=false)
        FaxConfig inactiveConfig1 = createFaxConfig(1, false, true);
        FaxConfig inactiveConfig2 = createFaxConfig(2, false, true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(inactiveConfig1, inactiveConfig2));

        // When
        faxImporter.poll();

        // Then: No provider client calls should be made
        verify(faxProviderClientFactory, never()).getClient(any());
        verify(faxProviderClient, never()).listInboundFaxes(any());
    }

    @Test
    @DisplayName("should skip configs with download disabled when polling")
    void shouldSkipConfigsWithDownloadDisabled_whenPolling() throws FaxProviderException {
        // Given: Two configs, both active but download=false
        FaxConfig noDownload1 = createFaxConfig(1, true, false);
        FaxConfig noDownload2 = createFaxConfig(2, true, false);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(noDownload1, noDownload2));

        // When
        faxImporter.poll();

        // Then: No provider client calls should be made
        verify(faxProviderClientFactory, never()).getClient(any());
        verify(faxProviderClient, never()).listInboundFaxes(any());
    }

    @Test
    @DisplayName("should continue to next account when provider throws FaxProviderException")
    void shouldContinueNextAccount_whenProviderThrowsFaxProviderException() throws FaxProviderException {
        // Given: Two active configs with download enabled
        FaxConfig failingConfig = createFaxConfig(1, true, true);
        FaxConfig succeedingConfig = createFaxConfig(2, true, true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(failingConfig, succeedingConfig));

        // First config throws FaxProviderException during getClient
        FaxProviderClient failingClient = mock(FaxProviderClient.class);
        FaxProviderClient succeedingClient = mock(FaxProviderClient.class);
        when(faxProviderClientFactory.getClient(failingConfig)).thenReturn(failingClient);
        when(faxProviderClientFactory.getClient(succeedingConfig)).thenReturn(succeedingClient);

        when(failingClient.listInboundFaxes(failingConfig))
                .thenThrow(new FaxProviderException("SRFax API timeout"));
        when(succeedingClient.listInboundFaxes(succeedingConfig))
                .thenReturn(Collections.emptyList());

        // When
        faxImporter.poll();

        // Then: Second config should still be processed
        verify(faxProviderClientFactory).getClient(failingConfig);
        verify(faxProviderClientFactory).getClient(succeedingConfig);
        verify(succeedingClient).listInboundFaxes(succeedingConfig);
    }

    @Test
    @DisplayName("should continue to next account when RuntimeException occurs")
    void shouldContinueNextAccount_whenRuntimeExceptionOccurs() throws FaxProviderException {
        // Given: Two active configs with download enabled
        FaxConfig crashingConfig = createFaxConfig(1, true, true);
        FaxConfig normalConfig = createFaxConfig(2, true, true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(crashingConfig, normalConfig));

        // First config throws RuntimeException during getClient
        when(faxProviderClientFactory.getClient(crashingConfig))
                .thenThrow(new RuntimeException("NullPointerException deep in stack"));
        when(faxProviderClientFactory.getClient(normalConfig)).thenReturn(faxProviderClient);
        when(faxProviderClient.listInboundFaxes(normalConfig))
                .thenReturn(Collections.emptyList());

        // When
        faxImporter.poll();

        // Then: Second config should still be processed despite first crashing
        verify(faxProviderClientFactory).getClient(crashingConfig);
        verify(faxProviderClientFactory).getClient(normalConfig);
        verify(faxProviderClient).listInboundFaxes(normalConfig);
    }

    @Test
    @DisplayName("should handle empty config list gracefully")
    void shouldHandleEmptyConfigList_gracefully() throws FaxProviderException {
        // Given: No fax configs at all
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.emptyList());

        // When
        faxImporter.poll();

        // Then: No errors, no provider client calls
        verify(faxProviderClientFactory, never()).getClient(any());
        verify(faxProviderClient, never()).listInboundFaxes(any());
    }

    @Test
    @DisplayName("should only process active configs with download enabled in mixed list")
    void shouldOnlyProcessActiveDownloadEnabled_inMixedList() throws FaxProviderException {
        // Given: Mix of active/inactive and download-enabled/disabled configs
        FaxConfig inactive = createFaxConfig(1, false, true);
        FaxConfig noDownload = createFaxConfig(2, true, false);
        FaxConfig inactiveNoDownload = createFaxConfig(3, false, false);
        FaxConfig activeWithDownload = createFaxConfig(4, true, true);

        List<FaxConfig> mixedConfigs = Arrays.asList(inactive, noDownload, inactiveNoDownload, activeWithDownload);
        when(faxConfigDao.findAll(null, null)).thenReturn(mixedConfigs);

        when(faxProviderClientFactory.getClient(activeWithDownload)).thenReturn(faxProviderClient);
        when(faxProviderClient.listInboundFaxes(activeWithDownload)).thenReturn(Collections.emptyList());

        // When
        faxImporter.poll();

        // Then: Only the active+download config should be processed
        verify(faxProviderClientFactory, times(1)).getClient(any());
        verify(faxProviderClientFactory).getClient(activeWithDownload);
        verify(faxProviderClient).listInboundFaxes(activeWithDownload);
    }

    @Test
    @DisplayName("should skip account and continue when IllegalStateException occurs from credential decryption")
    void shouldSkipAccountAndContinue_whenIllegalStateExceptionOccurs() throws FaxProviderException {
        // Given: Two active configs with download enabled - first has bad credentials
        FaxConfig badCredConfig = createFaxConfig(1, true, true);
        FaxConfig goodConfig = createFaxConfig(2, true, true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(badCredConfig, goodConfig));

        // First config: credential decryption fails with IllegalStateException
        when(faxProviderClientFactory.getClient(badCredConfig))
                .thenThrow(new IllegalStateException("Failed to decrypt faxPasswd"));

        // Second config: normal processing
        when(faxProviderClientFactory.getClient(goodConfig)).thenReturn(faxProviderClient);
        when(faxProviderClient.listInboundFaxes(goodConfig)).thenReturn(Collections.emptyList());

        // When
        faxImporter.poll();

        // Then: Second config should still be processed despite first having bad credentials
        verify(faxProviderClientFactory).getClient(badCredConfig);
        verify(faxProviderClientFactory).getClient(goodConfig);
        verify(faxProviderClient).listInboundFaxes(goodConfig);
    }

    @Test
    @DisplayName("should abort import and delete quarantined file when markFaxAsRead fails")
    @Tag("ringcentral")
    void shouldAbortImportAndDeleteQuarantinedFile_whenMarkFaxAsReadFails(@TempDir Path tempIncomingDir) throws Exception {
        // Pin the M1 contract: when Phase 2 mark-as-read throws, FaxImporter MUST delete the
        // quarantined file (so the next poll can re-fetch the still-unread message cleanly
        // without producing a duplicate EDoc) and MUST NOT proceed to Phase 3 (importFromIncoming
        // / providerRouting / deleteFax). The persisted FaxJob carries STATUS.ERROR so the
        // operator can see the failure on the dashboard.

        // Given: One active config with download enabled, faxIncomingDir wired to a temp dir
        FaxConfig config = createFaxConfig(42, true, true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
        installFaxIncomingDir(faxImporter, tempIncomingDir);

        when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);

        // Inbound fax with a real Base64-encoded PDF so saveToIncoming actually writes a file
        FaxJob inboundFax = new FaxJob();
        inboundFax.setFile_name("incoming.pdf");
        inboundFax.setStatus(FaxJob.STATUS.RECEIVED);
        when(faxProviderClient.listInboundFaxes(config))
                .thenReturn(Collections.singletonList(inboundFax));

        FaxJob downloaded = new FaxJob();
        downloaded.setDocument(Base64.getEncoder().encodeToString(buildMinimalPdf()));
        when(faxProviderClient.downloadFax(eq(config), any(FaxJob.class))).thenReturn(downloaded);

        // Mark-as-read fails — this is the trigger for the M1 abort path
        org.mockito.Mockito.doThrow(new FaxProviderException("RingCentral PUT readStatus failed"))
                .when(faxProviderClient).markFaxAsRead(eq(config), any(FaxJob.class));

        // When: poll() runs
        faxImporter.poll();

        // Then: deleteFax (Phase 3 ack) MUST NOT be called — Phase 3 is fully skipped
        verify(faxProviderClient, never()).deleteFax(any(FaxConfig.class), any(FaxJob.class));

        // Then: the quarantined file under faxIncomingDir/{configId}/ is gone — the directory
        // may exist (saveToIncoming created it) but it should contain no fax files
        Path configSubdir = tempIncomingDir.resolve(String.valueOf(config.getId()));
        if (Files.exists(configSubdir)) {
            try (var stream = Files.list(configSubdir)) {
                assertThat(stream.filter(p -> p.toString().endsWith(".pdf")).count())
                        .as("Quarantined PDF must be deleted after mark-as-read failure to "
                                + "avoid duplicate import on next poll")
                        .isZero();
            }
        }

        // Then: a FaxJob is persisted with STATUS.ERROR and a message that signals the
        // mark-as-read failure to the operator
        ArgumentCaptor<FaxJob> persisted = ArgumentCaptor.forClass(FaxJob.class);
        verify(faxJobDao).persist(persisted.capture());
        assertThat(persisted.getValue().getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        assertThat(persisted.getValue().getStatusString())
                .containsIgnoringCase("Mark-as-read failed");
    }

    // -- helper methods --

    /**
     * Creates a FaxConfig with the specified active and download flags.
     *
     * @param id config identifier
     * @param active whether the config is active
     * @param download whether download is enabled
     * @return configured FaxConfig instance
     */
    private FaxConfig createFaxConfig(int id, boolean active, boolean download) {
        FaxConfig config = new FaxConfig();
        config.setId(id);
        config.setActive(active);
        config.setDownload(download);
        config.setFaxUser("user-" + id);
        config.setProviderType(FaxConfig.ProviderType.SRFAX);
        config.setQueue(1);
        return config;
    }

    /**
     * Wires {@code FaxImporter.faxIncomingDir} to a real directory via reflection — the only
     * way to test Phase 1/Phase 2 orchestration without invoking the real {@code @PostConstruct}
     * (which depends on class-load-time CarlosProperties resolution).
     */
    private static void installFaxIncomingDir(FaxImporter importer, Path dir) throws Exception {
        Field f = FaxImporter.class.getDeclaredField("faxIncomingDir");
        f.setAccessible(true);
        f.set(importer, dir);
    }

    /**
     * Builds a minimal valid single-page PDF using OpenPDF — needed because saveToIncoming
     * calls {@code validateAndCountPages} which rejects non-PDF / zero-page payloads. Returns
     * raw bytes so the caller can Base64-encode them onto a {@link FaxJob}.
     */
    private static byte[] buildMinimalPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            document.add(new Paragraph("M1 dedup test fixture"));
        } catch (Exception e) {
            throw new IOException("Failed to build PDF fixture: " + e.getMessage(), e);
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }
}
