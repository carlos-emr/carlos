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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.util.ExtPrint;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Concurrent requests render real PDFs and must never share their backing file.
 * Only note/header data sources are isolated; rendering, files and merging are real.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
class CaseManagementPrintConcurrencyUnitTest {
    @TempDir Path documents;
    private String originalDocuments;
    private final Queue<Path> ownedTemporaryFiles = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void configureDocuments() {
        originalDocuments = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documents.toString());
    }

    @AfterEach
    void restoreDocuments() {
        if (originalDocuments == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocuments);
    }

    @Test
    void shouldKeepSimultaneousPatientPdfsSeparate_whenBothRenderBeforeEitherIsMerged() throws Exception {
        CyclicBarrier beforeFiles = new CyclicBarrier(2);
        CyclicBarrier duringRender = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> render(901001, "FAKE first patient exclusive body", beforeFiles, duringRender, false));
            var second = workers.submit(() -> render(901002, "FAKE second patient exclusive body", beforeFiles, duringRender, false));
            Rendered a = first.get(40, TimeUnit.SECONDS);
            Rendered b = second.get(40, TimeUnit.SECONDS);
            assertThat(a.exclusiveFile()).as("first request has its own backing inode").isTrue();
            assertThat(b.exclusiveFile()).as("second request has its own backing inode").isTrue();
            assertThat(a.text()).contains("FAKE first patient exclusive body").doesNotContain("FAKE second patient exclusive body");
            assertThat(b.text()).contains("FAKE second patient exclusive body").doesNotContain("FAKE first patient exclusive body");
        }
        for (Path owned : ownedTemporaryFiles) assertThat(owned).doesNotExist();
        try (var files = Files.list(documents)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test
    void shouldRemoveThePartialPatientPdf_whenAnExtensionFailsAfterRendering() throws Exception {
        assertThatThrownBy(() -> render(901003, "FAKE partial print body", null, null, true))
                .isInstanceOf(java.io.IOException.class);
        for (Path owned : ownedTemporaryFiles) assertThat(owned).doesNotExist();
        try (var files = Files.list(documents)) { assertThat(files.toList()).isEmpty(); }
    }

    private Rendered render(int patient, String body, CyclicBarrier beforeFiles,
                            CyclicBarrier duringRender, boolean fail) throws Exception {
        CaseManagementManager manager = mock(CaseManagementManager.class);
        CaseManagementNote note = new CaseManagementNote();
        note.setId((long) patient);
        note.setDemographic_no(Integer.toString(patient));
        note.setProviderNo("991001");
        note.setObservation_date(new Date(1790000000000L));
        note.setNote(body);
        when(manager.getNote(Integer.toString(patient))).thenAnswer(invocation -> {
            // Both requests finish clinical lookup before either creates its output file.
            if (beforeFiles != null) beforeFiles.await(20, TimeUnit.SECONDS);
            return note;
        });
        when(manager.getDemoName(Integer.toString(patient))).thenReturn("FAKE Patient " + patient);
        when(manager.getDemoGender(Integer.toString(patient))).thenReturn("U");
        when(manager.getDemoAge(Integer.toString(patient))).thenReturn("26");
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("991001");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", Integer.toString(patient));
        request.setParameter("extPrintConcurrency", "true");
        ChartProgramContext.remember(request, "0");
        boolean[] exclusive = {false};
        ExtPrint extension = (printer, scoped) -> {
            if (fail) throw new IllegalStateException("Synthetic print extension failure");
            FileLock lock = null;
            try {
                try {
                    lock = ((FileOutputStream) printer.getOutputStream()).getChannel().tryLock();
                    exclusive[0] = lock != null;
                } catch (OverlappingFileLockException sharedFile) {
                    exclusive[0] = false;
                }
                // Keep both files open through real rendering before merging/deleting either.
                duringRender.await(20, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new IllegalStateException("Concurrent renderer barrier failed", failure);
            } finally {
                if (lock != null) try { lock.release(); } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Could not release synthetic fixture file lock", failure);
                }
            }
        };
        try (var spring = mockStatic(SpringUtils.class); var clinic = mockConstruction(ClinicData.class);
             var paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            // Observe exact owned paths while retaining the real secure-file factory and filesystem.
            paths.when(() -> PathValidationUtils.createSecureTempFile(anyString(), anyString())).thenAnswer(invocation -> {
                java.io.File actual = (java.io.File) invocation.callRealMethod();
                ownedTemporaryFiles.add(actual.toPath());
                return actual;
            });
            spring.when(() -> SpringUtils.getBean(CaseManagementManager.class)).thenReturn(manager);
            spring.when(() -> SpringUtils.getBean("extPrintConcurrency")).thenReturn(extension);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new CaseManagementPrint().doPrint(login, patient, false, new String[]{Integer.toString(patient)},
                    false, false, false, false, false, false, null, null, request, bytes);
            try (var pdf = Loader.loadPDF(bytes.toByteArray())) {
                return new Rendered(exclusive[0], new PDFTextStripper().getText(pdf));
            }
        }
    }

    private record Rendered(boolean exclusiveFile, String text) { }
}
