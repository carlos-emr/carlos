/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;

@DisplayName("OHIP OhipClaimExtractService")
@Tag("unit")
@Tag("billing")
class OhipClaimExtractServiceUnitTest {

    @Test
    void shouldCalculateTotalsWithoutBinaryFloatingPointRounding() {
        BillingDao billingDao = mock(BillingDao.class);
        BillingDetailDao billingDetailDao = mock(BillingDetailDao.class);
        Billing billing = mock(Billing.class);
        BillingDetail detail = mock(BillingDetail.class);

        when(billing.getId()).thenReturn(100);
        when(billing.getClinicNo()).thenReturn(0);
        when(billing.getDemographicName()).thenReturn("Jane Test");
        when(billing.getHin()).thenReturn("1234567890");
        when(billing.getDob()).thenReturn("19700101");
        when(billing.getVisitDate()).thenReturn(new java.util.Date());
        when(billing.getVisitType()).thenReturn("00");
        when(billing.getClinicRefCode()).thenReturn("");
        when(billing.getStatus()).thenReturn("O");
        when(billing.getContent()).thenReturn("");

        when(detail.getServiceCode()).thenReturn("A001A");
        when(detail.getBillingAmount()).thenReturn("1.005");
        when(detail.getDiagnosticCode()).thenReturn(":::");
        when(detail.getBillingUnit()).thenReturn("1");
        when(detail.getAppointmentDate()).thenReturn(new java.util.Date());

        when(billingDao.findByProviderStatusAndDates(eq("123456"), anyList(), any()))
                .thenReturn(List.of(billing));
        when(billingDetailDao.findByBillingNoAndStatus(100, "O"))
                .thenReturn(List.of(detail));

        OhipClaimExtractService extract = new OhipClaimExtractService(billingDao, billingDetailDao);
        extract.seteFlag("0");
        extract.setOhipVer("V03");
        extract.setProviderNo("123456");
        extract.setOhipCenter("T");
        extract.setGroupNo("0000");
        extract.setSpecialty("00");
        extract.setBatchCount("1");

        extract.dbQuery();

        assertThat(extract.getTotalAmount()).isEqualTo("0.0101");
    }

    /**
     * Pins the {@code BillingFileWriteException} contract on the write-path:
     * {@code writeFile} and {@code writeHtml} both go through
     * {@code PathValidationUtils.validatePath}, then write via
     * {@code FileOutputStream}. Both reach a swallow-and-log site historically,
     * which the recent fix wave turned into {@code BillingFileWriteException}
     * throws. Without these tests, a future refactor reverting either path to
     * a {@code catch (Exception) { logger.error(...); }} would pass CI.
     */
    @Nested
    @DisplayName("write-path exception propagation")
    class WritePath {

        // Field-level @TempDir is shared across @BeforeEach and every @Test
        // method in this nested class — declaring @TempDir as a method
        // parameter creates a fresh temp dir per parameter, so the setUp
        // and test would point at different directories.
        @TempDir
        Path tempDir;

        private OhipClaimExtractService extract;
        private Object homeDirBefore;

        @BeforeEach
        void setUp() {
            BillingDao billingDao = mock(BillingDao.class);
            BillingDetailDao billingDetailDao = mock(BillingDetailDao.class);
            extract = new OhipClaimExtractService(billingDao, billingDetailDao);

            // Point HOME_DIR at the JUnit @TempDir so writeFile / writeHtml have
            // a real (writable) base directory. The FileOutputStream concats
            // home_dir + ohipFilename literally — no separator added — so the
            // base path must end in '/'.
            homeDirBefore = CarlosProperties.getInstance().get("HOME_DIR");
            CarlosProperties.getInstance().setProperty(
                    "HOME_DIR", tempDir.toString() + File.separator);
        }

        @AfterEach
        void tearDown() {
            // Restore prior HOME_DIR so we don't leak test state across the
            // CarlosProperties singleton into other test classes.
            if (homeDirBefore == null) {
                CarlosProperties.getInstance().remove("HOME_DIR");
            } else {
                CarlosProperties.getInstance().put("HOME_DIR", homeDirBefore);
            }
        }

        @Test
        void shouldWriteFile_andLeaveContentOnDisk() throws IOException {
            extract.setOhipFilename("HCPv03.txt");

            extract.writeFile("HE B0001234V03 ...");

            File written = new File(tempDir.toFile(), "HCPv03.txt");
            assertThat(written).exists();
            assertThat(Files.readString(written.toPath())).contains("HE B0001234V03");
        }

        @Test
        void shouldThrowBillingFileWriteException_whenWriteFileTargetIsADirectory() {
            // FileOutputStream throws FileNotFoundException when the target
            // path is an existing directory — a realistic failure shape for
            // an admin who manually pre-created the file as a folder.
            File asDir = new File(tempDir.toFile(), "claim.000");
            assertThat(asDir.mkdir()).isTrue();
            extract.setOhipFilename("claim.000");

            assertThatThrownBy(() -> extract.writeFile("payload"))
                    .isInstanceOf(BillingFileWriteException.class)
                    .hasMessageContaining("OHIP claim file");
        }

        @Test
        void shouldWriteHtml_andLeaveContentOnDisk() throws IOException {
            extract.setHtmlFilename("HCPv03.html");

            extract.writeHtml("<table><tr><td>HE B0001234</td></tr></table>");

            File written = new File(tempDir.toFile(), "HCPv03.html");
            assertThat(written).exists();
            assertThat(Files.readString(written.toPath())).contains("HE B0001234");
        }

        @Test
        void shouldThrowBillingFileWriteException_whenWriteHtmlTargetIsADirectory() {
            File asDir = new File(tempDir.toFile(), "report.html");
            assertThat(asDir.mkdir()).isTrue();
            extract.setHtmlFilename("report.html");

            assertThatThrownBy(() -> extract.writeHtml("<html/>"))
                    .isInstanceOf(BillingFileWriteException.class)
                    .hasMessageContaining("HTML companion file");
        }
    }
}
