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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService;
import io.github.carlos_emr.carlos.util.OscarDownload;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link BillingONDiskNameDao}.
 *
 * <p>Tests persist, findByGroupNo, getPrevDiskCreateDate, and
 * findByCreateDateRangeAndStatus methods with meaningful assertions.</p>
 *
 * @since 2026-03-07
 * @see BillingONDiskNameDao
 */
@DisplayName("BillingONDiskNameDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
public class BillingONDiskNameDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingONDiskNameDao dao;

    @Autowired
    private BillingONFilenameDao filenameDao;

    @Autowired
    private BillActivityDao activityDao;

    @PersistenceContext
    private EntityManager entityManager;

    @TempDir
    Path billingDirectory;

    /** Checks actual download and regeneration IO against durable review metadata. */
    @Test
    void shouldGateDownloadAndRegeneration_whenImportedFileNeedsReconciliation() throws Exception {
        String original = "HJ999998.001";
        String allocated = "o19-claim-2021-990102-0.claim";
        String claim = "HEH" + " ".repeat(20) + "00000042" + "HCP" + " ".repeat(45) + "\n";
        Files.writeString(billingDirectory.resolve(original), claim);
        entityManager.createNativeQuery("INSERT INTO billing_filename_import "
                + "(source_table, source_id, original_filename, imported_filename, group_size, reason, file_status) "
                + "VALUES ('billing_on_diskname', 990102, ?1, ?2, 2, 'collision', 'ambiguous')")
                .setParameter(1, original).setParameter(2, allocated).executeUpdate();
        var properties = CarlosProperties.getInstance();
        Object previousHome = properties.get("HOME_DIR");
        properties.setProperty("HOME_DIR", billingDirectory.toString() + File.separator);
        try {
            // The DAO harness excludes prototype services. Keep the real
            // audit DAO and IO code, mocking unrelated claim-extraction inputs.
            var sites = org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.SiteDao.class);
            org.mockito.Mockito.when(sites.getAllSites()).thenReturn(java.util.List.of());
            var writer = new OhipClaimFileService(
                    org.mockito.Mockito.mock(io.github.carlos_emr.carlos.managers.DemographicManager.class),
                    org.mockito.Mockito.mock(BillingONCHeader1Dao.class),
                    org.mockito.Mockito.mock(BillingONHeaderDao.class), filenameDao, sites,
                    org.mockito.Mockito.mock(BillingONItemDao.class),
                    org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.BillingServiceDao.class), dao,
                    org.mockito.Mockito.mock(io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService.class));
            writer.setOhipFilename(allocated);
            assertThat(download(allocated).getStatus()).isEqualTo(409);
            assertThatThrownBy(writer::readInBillingNo).isInstanceOf(
                    BillingFileWriteException.class);
            assertThat(Files.exists(billingDirectory.resolve(allocated))).isFalse();

            // This synthetic fixture supplies known provenance; production
            // reconciliation must establish that ownership before this step.
            Files.copy(billingDirectory.resolve(original), billingDirectory.resolve(allocated));
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(claim.getBytes(StandardCharsets.UTF_8)));
            entityManager.createNativeQuery("UPDATE billing_filename_import SET file_status='reconciled', "
                    + "file_sha256=?1, reconciled_by='fixture-reviewer', reconciled_at=CURRENT_TIMESTAMP "
                    + "WHERE source_table='billing_on_diskname' AND source_id=990102")
                    .setParameter(1, sha).executeUpdate();
            var response = download(allocated);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).isEqualTo(claim.getBytes(StandardCharsets.UTF_8));
            assertThatCode(writer::readInBillingNo).doesNotThrowAnyException();
            writer.writeFile(claim + "changed output\n");
            assertThat(download(allocated).getStatus()).isEqualTo(409);
            assertThat(download(original).getStatus()).isEqualTo(409);
            assertThat(Files.readString(billingDirectory.resolve(original))).isEqualTo(claim);
        } finally {
            if (previousHome == null) properties.remove("HOME_DIR");
            else properties.put("HOME_DIR", previousHome);
        }
    }

    private MockHttpServletResponse download(String filename) throws Exception {
        var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", billingDirectory.toString());
        request.getSession().setAttribute("user", "999998");
        var response = new MockHttpServletResponse();
        new OscarDownload().service(request, response);
        return response;
    }

    @Test
    void shouldReserveAllHistoricalNames_whenOnlyAuditRetainsOriginal() {
        entityManager.createNativeQuery("INSERT INTO billing_filename_import "
                + "(source_table, source_id, original_filename, imported_filename, group_size, reason, file_status) "
                + "VALUES ('billing_on_filename', 990201, 'legacy.001', 'o19-html-2020-990201-0.html', 2, 'collision', 'ambiguous')")
                .executeUpdate();
        assertThat(dao.existsByOhipFilename("legacy.001")).isTrue();
        assertThat(filenameDao.existsByHtmlFilename("legacy.001")).isTrue();
        assertThat(activityDao.isImportedFilenameReserved("legacy.001")).isTrue();
        assertThat(dao.importedFilenameReviews("legacy.001")).hasSize(1);
        assertThat(dao.importedFilenameReviews("o19-html-2020-990201-0.html")).hasSize(1);
        assertThat(dao.importedFilenameReviews("Legacy.001")).isEmpty();
        assertThat(dao.existsByOhipFilename("unused.001")).isFalse();
    }

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should report whether a disk already holds an OHIP claim file name")
    void shouldReportOhipFilenameInUse_whenDiskHoldsExactName() throws Exception {
        BillingONDiskName lastYear = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(lastYear);
        lastYear.setOhipFilename("HJ054321.001");
        dao.persist(lastYear);

        assertThat(dao.existsByOhipFilename("HJ054321.001")).isTrue();
        assertThat(dao.existsByOhipFilename("HJ054321.002")).isFalse();
        assertThat(dao.existsByOhipFilename("HJ054321.00")).isFalse();
    }

    @Test
    @Tag("read")
    @DisplayName("should find by group number and return most recent record")
    void shouldFindByGroupNo_whenMatchingRecordExists() throws Exception {
        BillingONDiskName entity1 = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setGroupNo("GRP01");
        entity1.setCreateDateTime(new Date(1000000L));
        dao.persist(entity1);

        BillingONDiskName entity2 = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setGroupNo("GRP01");
        entity2.setCreateDateTime(new Date(2000000L));
        dao.persist(entity2);

        BillingONDiskName other = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(other);
        other.setGroupNo("GRP99");
        other.setCreateDateTime(new Date(3000000L));
        dao.persist(other);

        BillingONDiskName result = dao.findByGroupNo("GRP01");

        assertThat(result).isNotNull();
        assertThat(result.getGroupNo()).isEqualTo("GRP01");
        // Should return most recent by createDateTime DESC
        assertThat(result.getId()).isEqualTo(entity2.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should return null when no matching group number exists")
    void shouldReturnNull_whenNoMatchingGroupNo() throws Exception {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setGroupNo("EXISTS");
        dao.persist(entity);

        BillingONDiskName result = dao.findByGroupNo("NONEXISTENT");

        assertThat(result).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find previous disk create date before given date")
    void shouldFindPrevDiskCreateDate_whenOlderRecordExists() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date oldDate = cal.getTime();

        cal.set(2025, Calendar.JUNE, 1, 0, 0, 0);
        Date midDate = cal.getTime();

        cal.set(2025, Calendar.DECEMBER, 1, 0, 0, 0);
        Date recentDate = cal.getTime();

        cal.set(2025, Calendar.AUGUST, 1, 0, 0, 0);
        Date queryDate = cal.getTime();

        BillingONDiskName old = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(old);
        old.setGroupNo("GRP01");
        old.setCreateDateTime(oldDate);
        dao.persist(old);

        BillingONDiskName mid = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(mid);
        mid.setGroupNo("GRP01");
        mid.setCreateDateTime(midDate);
        dao.persist(mid);

        BillingONDiskName recent = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(recent);
        recent.setGroupNo("GRP01");
        recent.setCreateDateTime(recentDate);
        dao.persist(recent);

        BillingONDiskName result = dao.getPrevDiskCreateDate(queryDate, "GRP01");

        assertThat(result).isNotNull();
        // Should return the most recent record BEFORE queryDate (midDate, not oldDate)
        assertThat(result.getId()).isEqualTo(mid.getId());
    }

    @Test
    @Tag("read")
    @DisplayName("should find by create date range and status")
    void shouldFindByCreateDateRangeAndStatus_whenMatchingRecordsExist() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(2025, Calendar.MARCH, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date inRangeDate = cal.getTime();

        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0);
        Date outOfRangeDate = cal.getTime();

        cal.set(2025, Calendar.MARCH, 1, 0, 0, 0);
        Date startDate = cal.getTime();

        cal.set(2025, Calendar.MARCH, 31, 23, 59, 59);
        Date endDate = cal.getTime();

        BillingONDiskName matching = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(matching);
        matching.setCreateDateTime(inRangeDate);
        matching.setStatus("A");
        dao.persist(matching);

        BillingONDiskName wrongStatus = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(wrongStatus);
        wrongStatus.setCreateDateTime(inRangeDate);
        wrongStatus.setStatus("D");
        dao.persist(wrongStatus);

        BillingONDiskName outOfRange = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(outOfRange);
        outOfRange.setCreateDateTime(outOfRangeDate);
        outOfRange.setStatus("A");
        dao.persist(outOfRange);

        List<BillingONDiskName> results = dao.findByCreateDateRangeAndStatus(startDate, endDate, "A");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(matching.getId());
        assertThat(results.get(0).getStatus()).isEqualTo("A");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no records match date range and status")
    void shouldReturnEmptyList_whenNoRecordsMatchDateRangeAndStatus() throws Exception {
        BillingONDiskName entity = new BillingONDiskName();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setCreateDateTime(new Date());
        entity.setStatus("A");
        dao.persist(entity);

        Calendar cal = Calendar.getInstance();
        cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        Date startDate = cal.getTime();
        cal.set(2000, Calendar.JANUARY, 31, 23, 59, 59);
        Date endDate = cal.getTime();

        List<BillingONDiskName> results = dao.findByCreateDateRangeAndStatus(startDate, endDate, "A");

        assertThat(results).isEmpty();
    }
}
