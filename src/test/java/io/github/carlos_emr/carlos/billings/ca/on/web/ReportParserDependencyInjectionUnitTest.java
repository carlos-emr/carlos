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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingEdtObecOutputSpecificationParser;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimsErrorReportImportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@DisplayName("Report upload parsers")
@Tag("unit")
@Tag("billing")
class ReportParserDependencyInjectionUnitTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldCreateClaimsErrorImportServiceWithInjectedService() throws Exception {
        Path report = Files.createFile(tempDir.resolve("E-empty.txt"));

        try (FileInputStream input = new FileInputStream(report.toFile())) {
            BillingClaimsErrorReportImportService importService =
                    new BillingClaimsErrorReportImportService(
                            mock(BillingOnErrorReportService.class));
            // Empty file → import succeeds with verdict=true; pinning the
            // happy-path contract that no rows persist on an empty stream.
            assertThat(importService.importStream(input, "E-empty.txt").isVerdict()).isTrue();
        }
    }

    @Test
    void shouldCreateObecOutputParserWithInjectedRepositories() throws Exception {
        Path report = Files.createFile(tempDir.resolve("R-empty.txt"));

        try (FileInputStream input = new FileInputStream(report.toFile())) {
            BillingEdtObecOutputSpecificationParser parser =
                    new BillingEdtObecOutputSpecificationParser(
                            mock(LoggedInInfo.class),
                            input,
                            mock(BatchEligibilityDao.class),
                            mock(DemographicManager.class),
                            mock(ProviderDao.class));

            // BillingEdtObecOutputSpecificationParser still exposes verdict
            // as a public field — the round-7 encapsulation only applied to
            // the sibling BillingClaimsErrorReportParser.
            assertThat(parser.verdict).isTrue();
            assertThat(parser.getEdtObecOutputSpecificationRecords()).isEmpty();
        }
    }
}
