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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;

/**
 * Pins the {@code BillingFileWriteException} contract on the
 * {@link OhipClaimFileService} write-path: {@code writeFile} and
 * {@code writeHtml} must surface I/O failures rather than swallow them. The
 * 590-line service had no test partner before this refactor, so any regression
 * that demoted these throws to a swallow would otherwise pass CI silently.
 *
 * <p>Note that {@code OhipClaimFileService.writeFile} concatenates the
 * {@code HOME_DIR} property with the {@code ohipFilename} field directly via
 * {@code FileOutputStream(String)} — no {@code PathValidationUtils} on this
 * class (a should-fix follow-up). This test focuses on the exception-
 * propagation contract, not on path-traversal hardening.</p>
 */
@DisplayName("OhipClaimFileService write-path")
@Tag("unit")
@Tag("billing")
class OhipClaimFileServiceUnitTest {

    @TempDir
    Path tempDir;

    private OhipClaimFileService service;
    private BillingONHeaderDao headerDao;
    private Object homeDirBefore;

    @BeforeEach
    void setUp() {
        DemographicManager demographicManager = mock(DemographicManager.class);
        BillingONCHeader1Dao cheaderDao = mock(BillingONCHeader1Dao.class);
        headerDao = mock(BillingONHeaderDao.class);
        BillingONFilenameDao filenameDao = mock(BillingONFilenameDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        BillingONItemDao itemDao = mock(BillingONItemDao.class);
        BillingServiceDao billingServiceDao = mock(BillingServiceDao.class);
        BillingONDiskNameDao diskNameDao = mock(BillingONDiskNameDao.class);
        BillingOnLookupService lookupService = mock(BillingOnLookupService.class);

        // The constructor pre-caches site short names; an empty list keeps
        // the constructor work to a minimum without touching the database.
        when(siteDao.getAllSites()).thenReturn(Collections.emptyList());

        service = new OhipClaimFileService(
                demographicManager, cheaderDao, headerDao, filenameDao, siteDao,
                itemDao, billingServiceDao, diskNameDao, lookupService);

        // Point HOME_DIR at the temp dir; the service concats home_dir +
        // ohipFilename literally, so the path must end with a separator.
        homeDirBefore = CarlosProperties.getInstance().get("HOME_DIR");
        CarlosProperties.getInstance().setProperty(
                "HOME_DIR", tempDir.toString() + File.separator);
    }

    @AfterEach
    void tearDown() {
        if (homeDirBefore == null) {
            CarlosProperties.getInstance().remove("HOME_DIR");
        } else {
            CarlosProperties.getInstance().put("HOME_DIR", homeDirBefore);
        }
    }

    @Test
    void shouldWriteFile_andLeaveContentOnDisk() throws IOException {
        service.setOhipFilename("HCPv03.txt");

        service.writeFile("HE B0001234V03 ...");

        File written = new File(tempDir.toFile(), "HCPv03.txt");
        assertThat(written).exists();
        assertThat(Files.readString(written.toPath())).contains("HE B0001234V03");
    }

    @Test
    void shouldThrowBillingFileWriteException_whenWriteFileTargetIsADirectory() {
        // Concrete failure shape that exercises the catch block: pre-create
        // the target as a directory so FileOutputStream throws
        // FileNotFoundException, which the service must wrap and rethrow.
        File asDir = new File(tempDir.toFile(), "claim.000");
        assertThat(asDir.mkdir()).isTrue();
        service.setOhipFilename("claim.000");

        assertThatThrownBy(() -> service.writeFile("payload"))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("OHIP claim file")
                .hasMessageContaining("claim.000");
    }

    @Test
    void shouldThrowBillingFileWriteException_andPreserveCause_whenWriteFileFails() {
        File asDir = new File(tempDir.toFile(), "claim.001");
        assertThat(asDir.mkdir()).isTrue();
        service.setOhipFilename("claim.001");

        assertThatThrownBy(() -> service.writeFile("payload"))
                .isInstanceOf(BillingFileWriteException.class)
                .hasCauseInstanceOf(java.io.FileNotFoundException.class);
    }

    @Test
    void shouldWriteHtml_andLeaveContentOnDisk() throws IOException {
        service.setHtmlFilename("HCPv03.html");

        service.writeHtml("<table><tr><td>HE B0001234</td></tr></table>");

        File written = new File(tempDir.toFile(), "HCPv03.html");
        assertThat(written).exists();
        assertThat(Files.readString(written.toPath())).contains("HE B0001234");
    }

    @Test
    void shouldThrowBillingFileWriteException_whenWriteHtmlTargetIsADirectory() {
        File asDir = new File(tempDir.toFile(), "report.html");
        assertThat(asDir.mkdir()).isTrue();
        service.setHtmlFilename("report.html");

        assertThatThrownBy(() -> service.writeHtml("<html/>"))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("HTML companion file")
                .hasMessageContaining("report.html");
    }

    @Test
    void shouldPropagateBillingFileWriteException_whenWriterCloseFails() throws Exception {
        // Pin the close-time IOException contract: round 6/7 swapped
        // PrintStream → BufferedWriter precisely so a failure during the
        // final flush at close() surfaces. Without this test, a regression
        // that re-introduced PrintStream (which swallows close-time
        // IOException silently) would still pass the open-time tests.
        //
        // Force a close-time IOException by writing to a path on a
        // filesystem we then make read-only after the FileOutputStream
        // is opened — buffered data sits in memory until close() flushes.
        // On Linux, removing write permission on the parent directory
        // does NOT affect already-open FDs, so we instead pre-fill the
        // disk... too brittle. Instead, mock the construction of
        // FileOutputStream so its close() throws.
        service.setOhipFilename("claim.close.txt");

        try (org.mockito.MockedConstruction<java.io.FileOutputStream> ignored =
                     org.mockito.Mockito.mockConstruction(java.io.FileOutputStream.class,
                             (mockFos, ctx) -> {
                                 // Allow writes; throw FRESH IOException on each close
                                 // (try-with-resources suppression rejects same-instance throws).
                                 org.mockito.Mockito.doAnswer(inv -> {
                                     throw new java.io.IOException("simulated close-time flush failure");
                                 }).when(mockFos).close();
                             })) {

            assertThatThrownBy(() -> service.writeFile("HE B0001234V03 ..."))
                    .isInstanceOf(BillingFileWriteException.class)
                    .hasCauseInstanceOf(java.io.IOException.class)
                    .hasRootCauseMessage("simulated close-time flush failure");
        }
    }

    @Test
    void shouldPropagateBillingFileWriteException_whenHtmlWriterCloseFails() throws Exception {
        // Same close-time IOException contract for the HTML companion file.
        service.setHtmlFilename("report.close.html");

        try (org.mockito.MockedConstruction<java.io.FileOutputStream> ignored =
                     org.mockito.Mockito.mockConstruction(java.io.FileOutputStream.class,
                             (mockFos, ctx) -> {
                                 org.mockito.Mockito.doAnswer(inv -> {
                                     throw new java.io.IOException("simulated close-time flush failure");
                                 }).when(mockFos).close();
                             })) {

            assertThatThrownBy(() -> service.writeHtml("<html/>"))
                    .isInstanceOf(BillingFileWriteException.class)
                    .hasCauseInstanceOf(java.io.IOException.class)
                    .hasRootCauseMessage("simulated close-time flush failure");
        }
    }

    @Test
    void shouldThrowBillingDataLoadException_whenBatchHeaderRowMissing() {
        // Pin the typed throw on getBatchHeaderObj so a future regression
        // that demotes it to NPE (or to BillingFileWriteException) is caught.
        // BillingDataLoadException routes to billingDataLoadError.jsp via the
        // global Struts mapping; BillingFileWriteException would misdirect
        // the operator to "check disk space" when the actual problem is a
        // missing DB row.
        when(headerDao.find(org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);

        assertThatThrownBy(() -> service.getBatchHeaderObj("12345"))
                .isInstanceOf(BillingDataLoadException.class)
                .hasMessageContaining("batch_header bid=12345 not found")
                .satisfies(t -> {
                    BillingDataLoadException d = (BillingDataLoadException) t;
                    assertThat(d.phase())
                            .isEqualTo(BillingDataLoadException.Phase.BATCH_HEADER_LOOKUP);
                    assertThat(d.context()).containsEntry("bid", "12345");
                });
    }
}
