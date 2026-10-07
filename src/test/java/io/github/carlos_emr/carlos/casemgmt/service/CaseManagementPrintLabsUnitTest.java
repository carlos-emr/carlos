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
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises lab selection through the real chart-print entry point. */
@Tag("unit")
class CaseManagementPrintLabsUnitTest extends CarlosUnitTestBase {
    private static final int PATIENT = 900001;
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    @TempDir
    Path documentDirectory;

    @ParameterizedTest
    @CsvSource({
        "2026-03-08T04:59:59.999Z, false",
        "2026-03-08T05:00:00Z, true",
        "2026-03-08T07:00:00Z, true",
        "2026-03-09T03:59:59.999Z, true",
        "2026-03-09T04:00:00Z, false",
        ", false"
    })
    void shouldIncludeOnlySelectedCalendarDay_whenPrintingLabsAcrossSpringDst(String timestamp, boolean included) throws Exception {
        LabResultData result = lab("101", "FAKE-ONE", timestamp);
        assertThat(printLabs(List.of(result), true, true)).containsExactlyElementsOf(included ? List.of("101") : List.of());
    }

    @Test
    void shouldKeepLatestInRangeVersion_whenNewerVersionIsOutsideRange() throws Exception {
        assertThat(printLabs(List.of(
                lab("101", "FAKE-SAME", "2026-03-08T05:00:00Z"),
                lab("103", "FAKE-SAME", "2026-03-09T04:00:00Z"),
                lab("102", "FAKE-SAME", "2026-03-08T07:00:00Z")), true, true)).containsExactly("102");
    }

    @Test
    void shouldKeepEveryUnnumberedLab_whenBothAreInsideRange() throws Exception {
        assertThat(printLabs(List.of(
                lab("101", null, "2026-03-08T05:00:00Z"),
                lab("102", "", "2026-03-08T07:00:00Z")), true, true)).containsExactly("102", "101");
    }

    @Test
    void shouldPreserveUnrestrictedPrinting_whenDateRangeIsDisabled() throws Exception {
        assertThat(printLabs(List.of(
                lab("101", "FAKE-OLD", "2020-01-01T00:00:00Z"),
                lab("102", "FAKE-UNKNOWN", null),
                lab("103", "FAKE-NEW", "2030-01-01T00:00:00Z")), false, true)).containsExactly("103", "101", "102");
    }

    @Test
    void shouldSkipLabLookup_whenLabsAreNotSelected() throws Exception {
        assertThat(printLabs(List.of(), true, false)).isEmpty();
    }

    @Test
    void shouldPrintSingleReceivedLab_whenNativeTimestampIncludesFractionalSeconds() throws Exception {
        LabResultData result = new LabResultData();
        result.labType = LabResultData.HL7TEXT;
        result.segmentID = "101";
        result.accessionNumber = "FAKE-RECEIVED";
        result.dateTime = io.github.carlos_emr.carlos.util.NativeQueryValues.asString(
                java.time.LocalDateTime.ofInstant(Instant.parse("2026-03-08T12:00:00Z"), ZoneId.systemDefault()));
        assertThat(printLabs(List.of(result), true, true)).containsExactly("101");
    }

    private List<String> printLabs(List<LabResultData> stored, boolean useRange, boolean includeLabs) throws Exception {
        // CommonLabResultData resolves these static dependencies before Mockito
        // can intercept its constructor.
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(AllergyDao.class);
        createAndRegisterMock(CaseManagementManager.class);
        createAndRegisterMock(NoteService.class);
        createAndRegisterMock(ProgramManager2.class);
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(PreventionManager.class);
        CarlosProperties properties = mock(CarlosProperties.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(properties.getProperty("CMESort", "")).thenReturn("");
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDirectory.toString());
        List<String> printed = new ArrayList<>();
        try (var configuration = mockStatic(CarlosProperties.class);
             var notes = mockConstruction(CaseManagementPrintPdf.class);
             var labs = mockConstruction(CommonLabResultData.class, (mock, context) ->
                     when(mock.populateLabResultsData(login, "", String.valueOf(PATIENT), "", "", "", "U"))
                             .thenReturn(new ArrayList<>(stored)));
             var pdfs = mockConstruction(LabPDFCreator.class, (mock, context) ->
                     printed.add((String) context.arguments().get(1)));
             var merger = mockStatic(ConcatPDF.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            var day = GregorianCalendar.from(LocalDate.of(2026, 3, 8).atStartOfDay(ZONE));
            new CaseManagementPrint().doPrint(login, PATIENT, false, new String[0],
                    false, false, includeLabs, false, false, useRange,
                    useRange ? day : null, useRange ? day : null,
                    new MockHttpServletRequest(), new ByteArrayOutputStream());
            assertThat(labs.constructed()).hasSize(includeLabs ? 1 : 0);
            for (LabPDFCreator pdf : pdfs.constructed()) verify(pdf).printPdf();
            assertThat(notes.constructed()).hasSize(1);
            verify(notes.constructed().getFirst()).finish();
        }
        return printed;
    }

    private static LabResultData lab(String id, String accession, String timestamp) {
        LabResultData result = spy(new LabResultData());
        result.labType = LabResultData.HL7TEXT;
        result.segmentID = id;
        result.accessionNumber = accession;
        // Use exact instants so the regression does not depend on the JVM's default zone.
        doReturn(timestamp == null ? null : Date.from(Instant.parse(timestamp))).when(result).getDateObj();
        return result;
    }
}
