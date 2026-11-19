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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Every printed copy of a report is made by {@link HRMPDFCreator}. It refuses a report filed to a
 * patient the provider is locked out of before it reads the report, whoever asked for the copy.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRMPDFCreator patient access")
class HRMPDFCreatorPatientAccessUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4242;
    private static final int REPORT = 7;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final HRMDocumentDao documents = mock(HRMDocumentDao.class);
    private final HRMDocumentToDemographicDao links = mock(HRMDocumentToDemographicDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(HRMDocumentDao.class, documents);
        registerMock(HRMDocumentToDemographicDao.class, links);
        HRMDocument stored = new HRMDocument();
        stored.setReportFile("FAKE-report.xml");
        when(documents.findById(REPORT)).thenReturn(List.of(stored));
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(REPORT);
        link.setDemographicNo(PATIENT);
        when(links.findByHrmDocumentId(REPORT)).thenReturn(List.of(link));
    }

    @Test
    @DisplayName("should print a report filed to a patient the provider may read")
    void shouldPrint_whenPatientIsReadable() throws Exception {
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(true);
        HRMReport report = mock(HRMReport.class);
        when(report.getLegalName()).thenReturn("FAKE-Readable Patient");
        when(report.getFirstReportTextContent()).thenReturn("FAKE-Report body");

        try (var parser = mockStatic(HRMReportParser.class)) {
            parser.when(() -> HRMReportParser.parseReport(user, "FAKE-report.xml")).thenReturn(report);
            new HRMPDFCreator(output, REPORT, user).printPdf();
        }

        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("FAKE-Report body");
        }
    }

    @Test
    @DisplayName("should refuse a report filed to a patient the provider is locked out of, before reading it")
    void shouldRefuse_whenPatientIsLocked() {
        try (var parser = mockStatic(HRMReportParser.class)) {
            assertThatThrownBy(() -> new HRMPDFCreator(output, REPORT, user))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required patient read access");

            parser.verify(() -> HRMReportParser.parseReport(any(LoggedInInfo.class), anyString()), never());
        }
        verifyNoInteractions(documents);
        assertThat(output.size()).isZero();
    }

    @Test
    @DisplayName("should print a report filed to no patient without asking about any patient")
    void shouldPrint_whenReportIsFiledToNoPatient() throws Exception {
        when(links.findByHrmDocumentId(REPORT)).thenReturn(List.of());
        HRMReport report = mock(HRMReport.class);
        when(report.getFirstReportTextContent()).thenReturn("FAKE-Unfiled body");

        try (var parser = mockStatic(HRMReportParser.class)) {
            parser.when(() -> HRMReportParser.parseReport(user, "FAKE-report.xml")).thenReturn(report);
            new HRMPDFCreator(output, REPORT, user).printPdf();
        }

        assertThat(output.size()).isPositive();
        verify(security, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }
}
