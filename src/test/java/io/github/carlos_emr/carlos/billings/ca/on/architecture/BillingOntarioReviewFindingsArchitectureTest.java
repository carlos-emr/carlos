/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.billings.ca.on.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Ontario billing review finding architecture checks")
@Tag("unit")
@Tag("billing")
class BillingOntarioReviewFindingsArchitectureTest {

    @Test
    void sharedDaoInterfacesShouldNotExposeOntarioDtoTypes() throws Exception {
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
    void billingStrutsPackageShouldNotExposeOntarioGlobalMappingsToBcActions() throws Exception {
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
    void peripheralCopyrightProseShouldRemainIntact() throws Exception {
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
    void claimDtosShouldBeCamelCaseRecords() throws Exception {
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
}
