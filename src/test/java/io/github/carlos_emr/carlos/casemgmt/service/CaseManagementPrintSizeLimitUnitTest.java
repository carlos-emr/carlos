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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * #6161 review: a chart print whose caller set a byte limit ({@link CaseManagementPrint#limitCompletePdfBytes})
 * refuses a finished PDF over it with {@link CaseManagementPrint.CompletePdfTooLargeException}, passed through
 * {@code doPrint} itself, not wrapped in its generic print failure. Were it wrapped, Messenger would see an
 * ordinary failure and send the message with a "(N/A)" chart item instead of answering 413. FAKE notes only.
 */
@Tag("unit")
@DisplayName("Chart print refuses a finished PDF over its caller's limit")
class CaseManagementPrintSizeLimitUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 900012;
    private static final byte[] FINISHED = "%PDF-1.4 FAKE finished chart print".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path documentDirectory;

    private CarlosProperties properties;
    private LoggedInInfo login;

    @BeforeEach
    void setUp() {
        CaseManagementManager notes = createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        CaseManagementNote note = new CaseManagementNote();
        note.setId(31L);
        note.setDemographic_no(String.valueOf(PATIENT));
        note.setProviderNo("999998");
        note.setObservation_date(new Date(1_700_000_000_000L));
        note.setNote("FAKE note");
        when(notes.getNotes(String.valueOf(PATIENT))).thenReturn(List.of(note));
        when(notes.getNote("31")).thenReturn(note);
        properties = mock(CarlosProperties.class);
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
    }

    /** Runs an "All notes" print whose merge writes {@link #FINISHED}, into {@code out}. */
    private void print(CaseManagementPrint print, OutputStream out) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", Integer.toString(PATIENT));
        ChartProgramContext.remember(request, null);
        try (var configuration = mockStatic(CarlosProperties.class);
             var _ = mockConstruction(CaseManagementPrintPdf.class);
             var merger = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            merger.when(() -> ConcatPDF.concatRequired(anyList(), any(OutputStream.class)))
                    .thenAnswer(invocation -> {
                        OutputStream staged = invocation.getArgument(1);
                        staged.write(FINISHED);
                        return null;
                    });

            print.doPrint(login, PATIENT, true, new String[0], false, false, false, false, false, false,
                    null, null, request, out);
        }
    }

    @Test
    @DisplayName("should pass the size refusal through doPrint, not as its generic print failure")
    void shouldPassTheSizeRefusalThrough_whenTheFinishedPdfPassesTheLimit() {
        CaseManagementPrint print = new CaseManagementPrint();
        print.limitCompletePdfBytes(FINISHED.length - 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThatThrownBy(() -> print(print, out))
                .isExactlyInstanceOf(CaseManagementPrint.CompletePdfTooLargeException.class);
        assertThat(out.size()).as("not one byte of the print reached the caller").isZero();
    }

    @Test
    @DisplayName("should hand over a finished PDF of exactly the limit, and any PDF when no limit is set")
    void shouldHandOverThePdf_whenItFitsTheLimitOrNoneIsSet() throws Exception {
        CaseManagementPrint limited = new CaseManagementPrint();
        limited.limitCompletePdfBytes(FINISHED.length);
        ByteArrayOutputStream fits = new ByteArrayOutputStream();
        print(limited, fits);
        assertThat(fits.toByteArray()).isEqualTo(FINISHED);

        ByteArrayOutputStream unlimited = new ByteArrayOutputStream();
        print(new CaseManagementPrint(), unlimited);
        assertThat(unlimited.toByteArray()).isEqualTo(FINISHED);
    }

    @Test
    @DisplayName("should still report any other print failure as the generic print failure")
    void shouldWrapOtherFailures_asTheGenericPrintFailure() {
        CaseManagementPrint print = new CaseManagementPrint();
        // Within the limit, a failing copy to the caller is an ordinary print failure.
        print.limitCompletePdfBytes(FINISHED.length);
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("FAKE broken response");
            }
        };

        assertThatThrownBy(() -> print(print, broken))
                .isExactlyInstanceOf(IOException.class)
                .hasMessage("Failed to generate complete chart print PDF");
    }
}
