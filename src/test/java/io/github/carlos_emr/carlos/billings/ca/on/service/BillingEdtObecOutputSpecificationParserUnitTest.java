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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingEdtObecOutputSpecificationRecordDto;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.model.BatchEligibility;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit coverage for the OBEC output-specification parser and its fixed-width field extraction rules. */
@DisplayName("BillingEdtObecOutputSpecificationParser")
@Tag("unit")
@Tag("billing")
class BillingEdtObecOutputSpecificationParserUnitTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldParseFixedWidthRecord_andEnrichFromRepositories() throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        BatchEligibilityDao batchEligibilityDao = mock(BatchEligibilityDao.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        ProviderDao providerDao = mock(ProviderDao.class);

        Demographic demographic = mock(Demographic.class);
        when(demographic.getLastName()).thenReturn("Patient");
        when(demographic.getFirstName()).thenReturn("Pat");
        when(demographic.getDateOfBirth()).thenReturn("1980-02-03");
        when(demographic.getSex()).thenReturn("F");
        when(demographic.getProviderNo()).thenReturn(" 999998 ");
        when(demographicManager.searchByHealthCard(loggedInInfo, "1234567890"))
                .thenReturn(List.of(demographic));

        Provider provider = mock(Provider.class);
        when(provider.getLastName()).thenReturn("Doctor");
        when(providerDao.getProvider("999998")).thenReturn(provider);

        BatchEligibility eligibility = new BatchEligibility();
        eligibility.setMOHResponse("Eligible");
        when(batchEligibilityDao.find(5)).thenReturn(eligibility);

        Path report = tempDir.resolve("R-valid.txt");
        Files.writeString(report, fixedWidthLine("1234567890", "AB", "05", "20261231", "Second") + "\n");

        BillingEdtObecOutputSpecificationParser parser;
        try (FileInputStream input = new FileInputStream(report.toFile())) {
            parser = new BillingEdtObecOutputSpecificationParser(
                    loggedInInfo, input, batchEligibilityDao, demographicManager, providerDao);
        }

        assertThat(parser.verdict).isTrue();
        assertThat(parser.getEdtObecOutputSpecificationRecords()).singleElement()
                .satisfies(row -> {
                    BillingEdtObecOutputSpecificationRecordDto dto =
                            (BillingEdtObecOutputSpecificationRecordDto) row;
                    assertThat(dto.getHealthNo()).isEqualTo("1234567890");
                    assertThat(dto.getVersion()).isEqualTo("AB");
                    assertThat(dto.getResponseCode()).isEqualTo("05");
                    assertThat(dto.getLastName()).isEqualTo("Patient");
                    assertThat(dto.getFirstName()).isEqualTo("Pat");
                    assertThat(dto.getDOB()).isEqualTo("1980-02-03");
                    assertThat(dto.getSex()).isEqualTo("F");
                    assertThat(dto.getIdentifier()).isEqualTo("Doctor");
                    assertThat(dto.getMOH()).isEqualTo("Eligible");
                    assertThat(dto.getExpiry()).isEqualTo("20261231");
                    assertThat(dto.getSecondName()).isEqualTo("Second              ");
                });
    }

    @Test
    void shouldSetVerdictFalse_whenRecordLayoutIsTooShortForFixedWidthFields() throws Exception {
        Path report = tempDir.resolve("R-short.txt");
        Files.writeString(report, "1234567890AB05too-short\n");

        BillingEdtObecOutputSpecificationParser parser;
        try (FileInputStream input = new FileInputStream(report.toFile())) {
            parser = new BillingEdtObecOutputSpecificationParser(
                    mock(LoggedInInfo.class),
                    input,
                    mock(BatchEligibilityDao.class),
                    mock(DemographicManager.class),
                    mock(ProviderDao.class));
        }

        assertThat(parser.verdict).isFalse();
        assertThat(parser.getEdtObecOutputSpecificationRecords()).isEmpty();
        assertThat(parser.getAttemptedRecordCount()).isEqualTo(1);
    }

    @Test
    void shouldSetVerdictFalse_whenResponseCodeIsNotNumeric() throws Exception {
        Path report = tempDir.resolve("R-nonnumeric-response.txt");
        Files.writeString(report, fixedWidthLine("1234567890", "AB", "XX", "20261231", "Second") + "\n");

        BillingEdtObecOutputSpecificationParser parser;
        try (FileInputStream input = new FileInputStream(report.toFile())) {
            parser = new BillingEdtObecOutputSpecificationParser(
                    mock(LoggedInInfo.class),
                    input,
                    mock(BatchEligibilityDao.class),
                    mock(DemographicManager.class),
                    mock(ProviderDao.class));
        }

        assertThat(parser.verdict).isFalse();
        assertThat(parser.getEdtObecOutputSpecificationRecords()).isEmpty();
        assertThat(parser.getAttemptedRecordCount()).isEqualTo(1);
    }


    @Test
    void shouldClearPreviouslyParsedRows_whenLaterRecordLayoutIsMalformed() throws Exception {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        BatchEligibilityDao batchEligibilityDao = mock(BatchEligibilityDao.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        ProviderDao providerDao = mock(ProviderDao.class);
        when(demographicManager.searchByHealthCard(loggedInInfo, "1234567890"))
                .thenReturn(List.of());
        when(demographicManager.searchByHealthCard(loggedInInfo, "9999999999"))
                .thenReturn(List.of());

        Path report = tempDir.resolve("R-partial.txt");
        Files.writeString(report,
                fixedWidthLine("1234567890", "AB", "05", "20261231", "Second") + "\n"
                        + "9999999999AB05too-short\n");

        BillingEdtObecOutputSpecificationParser parser;
        try (FileInputStream input = new FileInputStream(report.toFile())) {
            parser = new BillingEdtObecOutputSpecificationParser(
                    loggedInInfo, input, batchEligibilityDao, demographicManager, providerDao);
        }

        assertThat(parser.verdict).isFalse();
        assertThat(parser.getEdtObecOutputSpecificationRecords()).isEmpty();
        assertThat(parser.getAttemptedRecordCount()).isEqualTo(2);
    }

    private static String fixedWidthLine(String hin, String version, String response,
                                         String expiry, String secondName) {
        // Pin the ministry file offsets the parser depends on so regressions
        // fail in one helper instead of being re-derived in every test body.
        StringBuilder line = new StringBuilder(" ".repeat(110));
        replace(line, 0, hin);
        replace(line, 10, version);
        replace(line, 12, response);
        replace(line, 27, expiry);
        replace(line, 85, String.format("%-20s", secondName));
        return line.toString();
    }

    private static void replace(StringBuilder target, int start, String value) {
        target.replace(start, start + value.length(), value);
    }
}
