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
package io.github.carlos_emr.carlos.billings.ca.on.architecture;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Architecture guard coverage for the Ontario billing review-findings refactor boundaries. */
@DisplayName("Ontario billing review finding architecture checks")
@Tag("unit")
@Tag("billing")
class BillingOntarioReviewFindingsArchitectureTest {

    @Test
    void shouldKeepSharedDaoInterfacesFreeOfOntarioDtoTypes_forArchitectureContract() throws Exception {
        List<Path> files = List.of(
                Path.of("src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAppointmentDao.java"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/commn/dao/RaDetailDao.java"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/commn/dao/ReportProviderDao.java"),
                Path.of("src/main/java/io/github/carlos_emr/carlos/commn/dao/DiagnosticCodeDao.java"));

        for (Path file : files) {
            assertThat(Files.readString(file))
                    .describedAs(file.toString())
                    .doesNotContain("billings.ca.on.dto");
        }
    }

    @Test
    void shouldKeepBillingStrutsPackage_fromExposingOntarioGlobalMappingsToBcActions() throws Exception {
        String struts = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml"));

        int billingPackage = struts.indexOf("<package name=\"billing\"");
        int billingOnPackage = struts.indexOf("<package name=\"billing-on\"");
        assertThat(billingPackage).isGreaterThanOrEqualTo(0);
        assertThat(billingOnPackage).isGreaterThan(billingPackage);
        String sharedBillingPackage = struts.substring(billingPackage, billingOnPackage);

        assertThat(sharedBillingPackage)
                .doesNotContain("<global-results>")
                .doesNotContain("<global-exception-mappings>")
                .doesNotContain("billingValidationError")
                .doesNotContain("BillingValidationException")
                .doesNotContain("BillingDataLoadException")
                .doesNotContain("billingFileWriteError")
                .doesNotContain("billingDataLoadError")
                .doesNotContain("/WEB-INF/jsp/billing/CA/ON/billingValidationError.jsp")
                .doesNotContain("/WEB-INF/jsp/billing/CA/ON/billingFileWriteError.jsp");
    }

    @Test
    void shouldKeepPeripheralCopyrightProseIntact_forArchitectureContract() throws Exception {
        assertThat(Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/commn/dao/DemographicDaoImpl.java")))
                .contains("This software was written for")
                .contains("Centre for Research on Inner City Health, St. Michael's Hospital,")
                .contains("Modifications made by Magenta Health in 2024.");
        assertThat(Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/utility/SpringUtils.java")))
                .contains("This software was written for")
                .contains("Centre for Research on Inner City Health, St. Michael's Hospital,");
        assertThat(Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/webserv/rest/ScheduleService.java")))
                .contains("This software was written for the")
                .contains("Department of Family Medicine")
                .contains("McMaster University");
    }

    @Test
    void shouldKeepClaimDtos_asCamelCaseRecords() throws Exception {
        String header = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/dto/BillingClaimHeaderDto.java"));
        String item = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/dto/BillingClaimItemDto.java"));

        assertThat(header)
                .contains("public record BillingClaimHeaderDto")
                .doesNotContain("getTransc_id")
                .doesNotContain("getRef_num");
        assertThat(item)
                .contains("public record BillingClaimItemDto")
                .doesNotContain("getTransc_id")
                .doesNotContain("getService_date");
    }

    @Test
    void shouldKeepLegacyOntarioDtosUsingCamelCaseFields_withCompatibilityAccessors() {
        assertThat(Arrays.stream(BillingProviderDto.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("hso_no")
                .contains("hsoNo");
        assertThat(Arrays.stream(BillingBatchHeaderDto.class.getDeclaredFields())
                .map(Field::getName))
                .doesNotContain("disk_id", "transc_id", "rec_id", "spec_id",
                        "moh_office", "batch_id", "group_num", "provider_reg_num",
                        "h_count", "r_count", "t_count", "batch_date")
                .contains("diskId", "transcId", "recId", "specId", "mohOffice",
                        "batchId", "groupNum", "providerRegNum", "hCount",
                        "rCount", "tCount", "batchDate");

        BillingProviderDto provider = new BillingProviderDto();
        provider.setHsoNo("HSO");
        assertThat(provider.getHso_no()).isEqualTo("HSO");
        provider.setHso_no("LEGACY");
        assertThat(provider.getHsoNo()).isEqualTo("LEGACY");

        BillingBatchHeaderDto header = new BillingBatchHeaderDto();
        header.setDiskId("42");
        header.setTransc_id("BH");
        header.setMohOffice("G");
        header.setBatch_date("2026-05-01");
        assertThat(header.getDisk_id()).isEqualTo("42");
        assertThat(header.getTranscId()).isEqualTo("BH");
        assertThat(header.getMoh_office()).isEqualTo("G");
        assertThat(header.getBatchDate()).isEqualTo("2026-05-01");
    }

    @Test
    void shouldKeepBillingDataLoadExceptionContextKeysFreeOfPhi_forArchitectureContract() throws Exception {
        List<String> forbiddenPhiKeys = List.of(
                "\"hin\"",
                "\"healthCard\"",
                "\"healthcard\"",
                "\"firstName\"",
                "\"lastName\"",
                "\"patientName\"",
                "\"address\"",
                "\"diagnosis\"",
                "\"diagCode\"");

        try (Stream<Path> paths = Files.walk(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on"))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("BillingDataLoadException")) {
                    continue;
                }
                assertThat(source)
                        .describedAs("BillingDataLoadException context in %s must use PHI-safe keys", file)
                        .doesNotContain(forbiddenPhiKeys.toArray(String[]::new));
            }
        }
    }

    @Test
    void shouldKeepStrutsFiltersForwardEnabled_forDocumentUploadCompatibility() throws Exception {
        String webXml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"));

        assertThat(filterMappingFor(webXml, "struts2-prepare"))
                .contains("<dispatcher>FORWARD</dispatcher>");
        assertThat(filterMappingFor(webXml, "struts2-execute"))
                .contains("<dispatcher>FORWARD</dispatcher>");
    }

    private static String filterMappingFor(String webXml, String filterName) {
        String filterNameElement = "<filter-name>" + filterName + "</filter-name>";
        int searchFrom = 0;
        while (true) {
            int mappingStart = webXml.indexOf("<filter-mapping>", searchFrom);
            assertThat(mappingStart)
                    .as("filter mapping for %s", filterName)
                    .isGreaterThanOrEqualTo(0);
            int mappingEnd = webXml.indexOf("</filter-mapping>", mappingStart);
            String mapping = webXml.substring(mappingStart, mappingEnd + "</filter-mapping>".length());
            if (mapping.contains(filterNameElement)) {
                return mapping;
            }
            searchFrom = mappingEnd + "</filter-mapping>".length();
        }
    }
}
