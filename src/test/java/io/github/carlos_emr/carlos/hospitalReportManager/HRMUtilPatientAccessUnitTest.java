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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * {@link HRMUtil} is what the chart's HRM panel, the HRM popup and every attachment picker read a
 * patient's reports through, and what turns a report into an attachment. It applies the patient's
 * own access rules itself, so no caller can list, name or print a report of a patient the provider
 * is locked out of.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRMUtil patient access")
class HRMUtilPatientAccessUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 4242;
    private static final int REPORT = 7;

    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final HRMDocumentDao documents = mock(HRMDocumentDao.class);
    private final HRMDocumentToDemographicDao links = mock(HRMDocumentToDemographicDao.class);
    private final NioFileManager files = mock(NioFileManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private String previousBillRegion;
    private MockedStatic<HRMReportParser> parser;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(HRMDocumentDao.class, documents);
        registerMock(HRMDocumentToDemographicDao.class, links);
        registerMock(HRMCategoryDao.class, mock(HRMCategoryDao.class));
        registerMock(HRMSubClassDao.class, mock(HRMSubClassDao.class));
        registerMock(HRMDocumentSubClassDao.class, mock(HRMDocumentSubClassDao.class));
        registerMock(NioFileManager.class, files);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(true);
        // HRM lists nothing outside Ontario billing.
        previousBillRegion = CarlosProperties.getInstance().getProperty("billregion");
        CarlosProperties.getInstance().setProperty("billregion", "ON");

        HRMDocument document = new HRMDocument();
        document.setReportFile("FAKE-report.xml");
        document.setReportType("FAKE-Type");
        document.setDescription("FAKE-Description");
        document.setTimeReceived(new Date(1_700_000_000_000L));
        when(documents.findById(REPORT)).thenReturn(List.of(document));
        when(documents.find((Object) Integer.valueOf(REPORT))).thenReturn(document);
        HRMReport report = mock(HRMReport.class);
        when(report.getFirstReportClass()).thenReturn("Medical Records Report");
        parser = mockStatic(HRMReportParser.class);
        parser.when(() -> HRMReportParser.parseReport(user, "FAKE-report.xml")).thenReturn(report);

        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(REPORT);
        link.setDemographicNo(PATIENT);
        when(links.findByDemographicNo(Integer.toString(PATIENT))).thenReturn(List.of(link));
        when(links.findByHrmDocumentId(REPORT)).thenReturn(List.of(link));
    }

    @AfterEach
    void tearDown() {
        parser.close();
        if (previousBillRegion == null) {
            CarlosProperties.getInstance().remove("billregion");
        } else {
            CarlosProperties.getInstance().setProperty("billregion", previousBillRegion);
        }
    }

    private void permitPatient() {
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", "r", Integer.toString(PATIENT))).thenReturn(true);
        when(security.hasPrivilege(user, "_hrm", "r", Integer.toString(PATIENT))).thenReturn(true);
    }

    @Test
    @DisplayName("should list the reports of a patient the provider may read")
    void shouldListReports_whenPatientIsReadable() {
        permitPatient();

        var listed = HRMUtil.listHRMDocuments(user, "report_date", false, Integer.toString(PATIENT), false);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("description")).isEqualTo("FAKE-Description");
    }

    @Test
    @DisplayName("should list nothing, and read no link or report, for a patient the provider is locked out of")
    void shouldListNothing_whenPatientIsLocked() {
        permitPatient();
        when(security.isAllowedAccessToPatientRecord(user, PATIENT)).thenReturn(false);

        var listed = HRMUtil.listHRMDocuments(user, "report_date", false, Integer.toString(PATIENT), true);

        assertThat(listed).isEmpty();
        verifyNoInteractions(links, documents);
        parser.verify(() -> HRMReportParser.parseReport(any(LoggedInInfo.class), anyString()), never());
        // The refusal is audited as one ("no right"), under the patient. The recent-patient lists
        // leave that action out, so being refused does not list the patient as recently opened.
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog(eq("999998"),
                eq(io.github.carlos_emr.carlos.log.LogConst.NORIGHT), eq(io.github.carlos_emr.carlos.log.LogConst.CON_HRM),
                eq(Integer.toString(PATIENT)), org.mockito.ArgumentMatchers.nullable(String.class),
                eq(Integer.toString(PATIENT)), eq("list")));
    }

    @Test
    @DisplayName("should decide for the number the DAO will read, however it is written")
    void shouldListNothing_whenLockedPatientNumberIsWrittenWithSign() {
        var listed = HRMUtil.listHRMDocuments(user, "report_date", false, "+" + PATIENT, true);

        assertThat(listed).isEmpty();
        verifyNoInteractions(links, documents);
    }

    @Test
    @DisplayName("should name a report filed to a patient the provider may read")
    void shouldReturnDocument_whenPatientIsReadable() {
        permitPatient();

        assertThat(HRMUtil.getHRMDocumentById(user, REPORT).getDisplayName()).isEqualTo("FAKE-Description");
    }

    @Test
    @DisplayName("should refuse to name a report filed to a patient the provider is locked out of")
    void shouldRefuseDocument_whenPatientIsLocked() {
        assertThatThrownBy(() -> HRMUtil.getHRMDocumentById(user, REPORT))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required patient read access");

        verifyNoInteractions(documents);
        parser.verify(() -> HRMReportParser.parseReport(any(LoggedInInfo.class), anyString()), never());
    }

    @Test
    @DisplayName("should refuse to render a report filed to a patient the provider is locked out of")
    void shouldRefuseRender_whenPatientIsLocked() {
        try (var creators = mockConstruction(HRMPDFCreator.class)) {
            assertThatThrownBy(() -> HRMUtil.renderHRM(user, REPORT))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required patient read access");

            assertThat(creators.constructed()).isEmpty();
        }
        verifyNoInteractions(files, documents);
    }

    @Test
    @DisplayName("should render a report filed to no patient on the module right alone")
    void shouldRender_whenReportIsFiledToNoPatient() throws Exception {
        when(links.findByHrmDocumentId(REPORT)).thenReturn(List.of());
        Path saved = Path.of("synthetic-temporary.pdf").toAbsolutePath();
        when(files.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenReturn(saved);

        try (var creators = mockConstruction(HRMPDFCreator.class)) {
            assertThat(HRMUtil.renderHRM(user, REPORT)).isEqualTo(saved);
        }
        verify(security, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }
}
