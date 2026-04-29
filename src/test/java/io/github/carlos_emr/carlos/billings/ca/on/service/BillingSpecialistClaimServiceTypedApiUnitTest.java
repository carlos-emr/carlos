/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingSpecialistClaim;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingSpecialistClaimCommand;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("BillingSpecialistClaimService typed API")
@Tag("unit")
@Tag("billing")
class BillingSpecialistClaimServiceTypedApiUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldBuildClaimFromTypedRequestInsteadOfHttpServletRequest() {
        BillingOnClaimPersister persister = Mockito.mock(BillingOnClaimPersister.class);
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingSpecialistClaimService service = new BillingSpecialistClaimService(persister, serviceCodeLoader);
        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of("A001A", "Minor assessment", "10.00", "0.00"));

        BillingSpecialistClaimCommand request = new BillingSpecialistClaimCommand(
                "1234567890AB",
                "1980-01-01",
                "ON",
                "HCP",
                "P",
                "0000",
                "clinic1",
                "123",
                "123456|999998",
                "456",
                "Doe,Jane",
                "F",
                "2026-04-28",
                "A001A",
                "00",
                "250",
                "999998",
                "999998");

        BillingSpecialistClaim claim = service.buildBillingClaim(request);

        assertThat(claim.header().getHin()).isEqualTo("1234567890");
        assertThat(claim.header().getVer()).isEqualTo("AB");
        assertThat(claim.header().getCreator()).isEqualTo("999998");
        assertThat(claim.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getService_code()).isEqualTo("A001A");
                    assertThat(item.getFee()).isEqualTo("10.00");
                    assertThat(item.getDx()).isEqualTo("250");
                });
    }

    @Test
    void shouldNotExposeServletOrRawCarrierApi() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/billings/ca/on/service/BillingSpecialistClaimService.java"));

        assertThat(source).doesNotContain("HttpServletRequest");
        assertThat(source).doesNotContain("getBillingClaimObj(");
        assertThat(source).doesNotContain("getBillingClaimInrObj(");
        assertThat(source).doesNotContain("addABillingRecord(ArrayList");
    }
}
