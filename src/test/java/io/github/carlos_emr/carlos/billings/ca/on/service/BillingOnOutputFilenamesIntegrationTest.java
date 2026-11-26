/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Tag("dao")
@Tag("billing-on")
@Transactional
class BillingOnOutputFilenamesIntegrationTest extends CarlosTestBase {
    @PersistenceContext private EntityManager em;
    @TempDir private Path directory;
    private Object previousHome;
    private BillingOnOutputFilenames outputs;

    @BeforeEach
    void setUp() {
        previousHome = CarlosProperties.getInstance().put("HOME_DIR", directory.toString());
        outputs = new BillingOnOutputFilenames();
        applicationContext.getAutowireCapableBeanFactory().autowireBean(outputs);
    }

    @AfterEach
    void restoreHome() {
        var properties = CarlosProperties.getInstance();
        if (previousHome == null) properties.remove("HOME_DIR");
        else properties.put("HOME_DIR", previousHome);
    }

    @Test
    void shouldReserveEveryHistoricalName_acrossBothGeneratorsAndImportEvidence() {
        var disk = new BillingONDiskName();
        disk.setOhipFilename("HJ817161.111");
        disk.setMonthCode("J");
        disk.setBatchCount(111);
        disk.setGroupNo("0000");
        disk.setCreator("817161");
        disk.setClaimRecord("1");
        disk.setStatus("D");
        disk.setTotal("1.00");
        disk.setCreateDateTime(Date.from(java.time.Instant.parse("2019-10-01T12:00:00Z")));
        disk.setTimestamp(Date.from(java.time.Instant.parse("2019-10-01T12:00:00Z")));
        em.persist(disk);
        var child = new BillingONFilename();
        child.setDiskId(disk.getId());
        child.setProviderNo("817161");
        child.setProviderOhipNo("817161");
        child.setHtmlFilename("HJ817161_111.html");
        child.setClaimRecord("1");
        child.setStatus("D");
        child.setTotal("1.00");
        child.setTimestamp(Date.from(java.time.Instant.parse("2019-10-01T12:00:00Z")));
        em.persist(child);
        var activity = new BillActivity();
        activity.setOhipFilename("HJ817161.112");
        activity.setHtmlFilename("HJ817161_112.htm");
        activity.setProviderOhipNo("817161");
        activity.setMonthCode("J");
        activity.setBatchCount(112);
        activity.setGroupNo("0000");
        activity.setCreator("817161");
        activity.setClaimRecord("1");
        activity.setStatus("D");
        activity.setTotal("1.00");
        activity.setUpdateDateTime(Date.from(java.time.Instant.parse("2019-10-01T12:00:00Z")));
        em.persist(activity);
        em.createNativeQuery("""
                INSERT INTO billing_filename_import (source_table, source_id, original_filename, imported_filename,
                    group_size, reason, file_status, lastUpdateUser, lastUpdateDate)
                VALUES ('billing_on_diskname', 817161, 'HJ817161.113', 'o19-fixture-817161.claim',
                    2, 'collision', 'ambiguous', 'fixture', CURRENT_TIMESTAMP)
                """).executeUpdate();
        for (String filename : List.of("HJ817161.111", "HJ817161_111.html", "HJ817161.112", "HJ817161_112.htm",
                "HJ817161.113", "o19-fixture-817161.claim")) {
            assertThat(outputs.isInUse(filename)).as(filename).isTrue();
        }
        assertThat(outputs.isInUse("HJ817161.114")).isFalse();
    }

    @Test
    void shouldPreservePhysicalEntries_whenMetadataIsMissing() throws Exception {
        var orphan = Files.writeString(directory.resolve("HJ817162.001"), "prior submission fixture");
        Files.createSymbolicLink(directory.resolve("HJ817162.002"), directory.resolve("absent"));
        Files.createDirectory(directory.resolve("HJ817162.003"));
        assertThat(outputs.isInUse("HJ817162.001")).isTrue();
        assertThat(outputs.isInUse("HJ817162.002")).isTrue();
        assertThat(outputs.isInUse("HJ817162.003")).isTrue();
        assertThat(outputs.isInUse("HJ817162.004")).isFalse();
        assertThat(Files.readString(orphan)).isEqualTo("prior submission fixture");
    }

    @Test
    void shouldRefuseAllocation_whenTheConfiguredFolderIsUnavailable() {
        CarlosProperties.getInstance().setProperty("HOME_DIR", directory.resolve("missing").toString());
        assertThatThrownBy(() -> outputs.isInUse("HJ817162.001"))
                .isInstanceOfSatisfying(BillingFileWriteException.class, failure ->
                        assertThat(failure.reason()).isEqualTo(BillingFileWriteException.Reason.OUTPUT_FOLDER_UNAVAILABLE));
    }
}
