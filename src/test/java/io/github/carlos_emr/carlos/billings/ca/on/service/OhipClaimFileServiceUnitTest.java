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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.BillingDates;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Pins the {@code BillingFileWriteException} contract on the
 * {@link OhipClaimFileService} write-path: {@code writeFile} and
 * {@code writeHtml} must surface I/O failures rather than swallow them. The
 * 590-line service had no test partner before this refactor, so any regression
 * that demoted these throws to a swallow would otherwise pass CI silently.
 *
 * <p>The write path also validates generated filenames with
 * {@code PathValidationUtils}; traversal attempts must fail before opening the
 * target stream.</p>
 */
@DisplayName("OhipClaimFileService write-path")
@Tag("unit")
@Tag("billing")
class OhipClaimFileServiceUnitTest {

    @TempDir
    Path tempDir;

    private OhipClaimFileService service;
    private BillingONHeaderDao headerDao;
    private DemographicManager demographicManager;
    private BillingONCHeader1Dao cheaderDao;
    private BillingONItemDao itemDao;
    private BillingServiceDao billingServiceDao;
    private BillingOnLookupService lookupService;
    private Object homeDirBefore;

    @BeforeEach
    void setUp() {
        demographicManager = mock(DemographicManager.class);
        cheaderDao = mock(BillingONCHeader1Dao.class);
        headerDao = mock(BillingONHeaderDao.class);
        BillingONFilenameDao filenameDao = mock(BillingONFilenameDao.class);
        SiteDao siteDao = mock(SiteDao.class);
        itemDao = mock(BillingONItemDao.class);
        billingServiceDao = mock(BillingServiceDao.class);
        BillingONDiskNameDao diskNameDao = mock(BillingONDiskNameDao.class);
        lookupService = mock(BillingOnLookupService.class);

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
    void shouldThrowBillingFileWriteException_andPreserveCauseWhenWriteFileFails() {
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
    void shouldDeleteGeneratedFilesQuietly_whenCleanupRequested() throws IOException {
        Path ohipFile = tempDir.resolve("claim.cleanup.txt");
        Path htmlFile = tempDir.resolve("claim.cleanup.html");
        Files.writeString(ohipFile, "ohip");
        Files.writeString(htmlFile, "html");
        service.setOhipFilename("claim.cleanup.txt");
        service.setHtmlFilename("claim.cleanup.html");

        service.deleteOhipFileQuietly();
        service.deleteHtmlFileQuietly();

        assertThat(ohipFile).doesNotExist();
        assertThat(htmlFile).doesNotExist();
    }

    @Test
    void shouldRestoreRenamedFileQuietly_whenRegenerationCleanupRequested() throws IOException {
        Path original = tempDir.resolve("claim.regen.txt");
        Files.writeString(original, "original file");
        service.setOhipFilename("claim.regen.txt");

        service.renameFile();
        assertThat(original).doesNotExist();

        service.restoreLastRenameQuietly();

        assertThat(original).exists();
        assertThat(Files.readString(original)).isEqualTo("original file");
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

    @Test
    void shouldCreateGoldenSimulation_forSingleHcpClaim() throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        BillingONCHeader1 header = hcpHeader();
        BillingONItem item = hcpItem();
        Demographic demographic = mock(Demographic.class);
        when(demographic.getRosterStatus()).thenReturn("RO");
        when(demographic.getBirthDayAsString()).thenReturn("1980-01-01");
        when(demographic.getSex()).thenReturn("F");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);
        when(lookupService.getPatientCurBillingDemo(loggedInInfo, "123"))
                .thenReturn(List.of("DOE", "JANE", "19800101", "1234567890", "AB", "ON", "F"));
        when(cheaderDao.findByProviderStatusAndDateRange(eq("999998"), eq(List.of("O")), any(DateRange.class)))
                .thenReturn(List.of(header));
        when(itemDao.findByCh1IdsExcludingDeletedAndSettled(List.of(12345678)))
                .thenReturn(List.of(item));
        when(billingServiceDao.codeRequiresSLI("A001A")).thenReturn(false);

        service.setProviderNo("999998");
        service.setDateRange(new DateRange(
                BillingDates.parseIsoDate("2026-04-01"),
                BillingDates.parseIsoDate("2026-04-30")));
        service.setEFlag("0");
        service.setContextPath("");

        service.createBillingFileStr(loggedInInfo, "0", new String[] {"O"}, true, "P", false);

        assertThat(service.getValue())
                .contains("\nHEH1234567890AB1980010112345678HCPP")
                .contains("\nHETA001A  0012340120260402401");
        assertThat(service.getRecordCount()).isEqualTo(1);
        assertThat(service.getOhipClaim()).isEqualTo("1");
        assertThat(service.getOhipRecord()).isEqualTo("1");
        assertThat(service.getTotalAmount()).isEqualTo("12.34");
        assertThat(service.getBigTotal()).isEqualByComparingTo("12.34");
        assertThat(service.getHtmlValue()).contains("Pass").contains("A001A");
        verify(itemDao).findByCh1IdsExcludingDeletedAndSettled(List.of(12345678));
        verify(itemDao, never()).findByCh1Id(12345678);
    }


    @Test
    void shouldSanitizeSummaryProviderToken_whenProviderNoContainsSelectorChars() throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        BillingONCHeader1 header = hcpHeader();
        header.setProviderNo("99.99/8");
        BillingONItem item = hcpItem();
        Demographic demographic = mock(Demographic.class);
        when(demographic.getRosterStatus()).thenReturn("RO");
        when(demographic.getBirthDayAsString()).thenReturn("1980-01-01");
        when(demographic.getSex()).thenReturn("F");
        when(demographicManager.getDemographic(loggedInInfo, "123")).thenReturn(demographic);
        when(lookupService.getPatientCurBillingDemo(loggedInInfo, "123"))
                .thenReturn(List.of("DOE", "JANE", "19800101", "1234567890", "AB", "ON", "F"));
        when(cheaderDao.findByProviderStatusAndDateRange(eq("99.99/8"), eq(List.of("O")), any(DateRange.class)))
                .thenReturn(List.of(header));
        when(itemDao.findByCh1IdsExcludingDeletedAndSettled(List.of(12345678)))
                .thenReturn(List.of(item));
        when(billingServiceDao.codeRequiresSLI("A001A")).thenReturn(false);

        service.setProviderNo("99.99/8");
        service.setDateRange(new DateRange(
                BillingDates.parseIsoDate("2026-04-01"),
                BillingDates.parseIsoDate("2026-04-30")));
        service.setEFlag("0");
        service.setContextPath("");

        service.createBillingFileStr(loggedInInfo, "0", new String[] {"O"}, true, "P", true);

        assertThat(service.getHtmlValue())
                .contains("id='recordShowButton99_99_8'")
                .contains("id='recordHideButton99_99_8'")
                .contains("class='record99_99_8'")
                .contains("jQuery(\".record99_99_8\")")
                .doesNotContain("record99.99/8");
    }

    @Test
    void shouldKeepMutableErrorStatePrivate_forArchitectureContract() throws Exception {
        assertThat(Modifier.isPrivate(
                OhipClaimFileService.class.getDeclaredField("errorFatalMsg").getModifiers()))
                .isTrue();
        assertThat(Modifier.isPrivate(
                OhipClaimFileService.class.getDeclaredField("errorMsg").getModifiers()))
                .isTrue();
        assertThat(Modifier.isPrivate(
                OhipClaimFileService.class.getDeclaredField("errorPartMsg").getModifiers()))
                .isTrue();
        assertThat(OhipClaimFileService.class.getDeclaredField("errorParams")).isNotNull();
    }

    @Test
    void shouldNotAdvertiseThreadSafety_onPrototypeErrorAccessors() throws Exception {
        assertThat(Modifier.isSynchronized(
                OhipClaimFileService.class.getMethod("setErrorMsg", String.class).getModifiers()))
                .isFalse();
        assertThat(Modifier.isSynchronized(
                OhipClaimFileService.class.getMethod("getErrorMsg").getModifiers()))
                .isFalse();
        assertThat(Modifier.isSynchronized(
                OhipClaimFileService.class.getMethod("setErrorParams", String[].class).getModifiers()))
                .isFalse();
        assertThat(Modifier.isSynchronized(
                OhipClaimFileService.class.getMethod("setProviderNo", String.class).getModifiers()))
                .isFalse();
    }

    @Test
    void shouldKeepPrototypeWriterOutsideClassLevelTransactions_forFileSystemConsistency() {
        assertThat(OhipClaimFileService.class.getAnnotation(Transactional.class)).isNull();
    }

    private static BillingONCHeader1 hcpHeader() throws Exception {
        BillingONCHeader1 header = new BillingONCHeader1();
        setEntityId(header, 12345678);
        header.setTranscId("HE");
        header.setRecId("H");
        header.setHin("1234567890");
        header.setVer("AB");
        header.setDob("19800101");
        header.setPayProgram("HCP");
        header.setPayee("P");
        header.setRefNum("");
        header.setFaciltyNum("0000");
        header.setAdmissionDate(null);
        header.setRefLabNum("");
        header.setManReview("");
        header.setLocation("0000");
        header.setDemographicNo(123);
        header.setProviderNo("999998");
        header.setAppointmentNo(0);
        header.setDemographicName("DOE,JANE");
        header.setSex("F");
        header.setProvince("ON");
        header.setBillingDate(BillingDates.parseIsoDate("2026-04-02"));
        header.setBillingTime(BillingDates.parseIsoTime("00:00:00"));
        header.setTotal(new BigDecimal("12.34"));
        header.setPaid(BigDecimal.ZERO);
        header.setStatus("O");
        header.setComment("");
        header.setVisitType("00");
        header.setProviderOhipNo("123456");
        header.setProviderRmaNo("");
        header.setApptProviderNo("999998");
        header.setAsstProviderNo("");
        header.setCreator("999998");
        header.setClinic("");
        return header;
    }

    private static BillingONItem hcpItem() {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(12345678);
        item.setTranscId("HE");
        item.setRecId("T");
        item.setServiceCode("A001A");
        item.setFee("12.34");
        item.setServiceCount("1");
        item.setServiceDate(BillingDates.parseIsoDate("2026-04-02"));
        item.setDx("401");
        item.setDx1("");
        item.setDx2("");
        item.setStatus("O");
        return item;
    }

    private static void setEntityId(Object entity, Integer id) throws Exception {
        java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
