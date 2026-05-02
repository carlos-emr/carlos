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
import static org.mockito.ArgumentMatchers.any;
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
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "A001A", "Minor assessment", "10.00", "0.00", "", "false")));

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
                    assertThat(item.serviceCode()).isEqualTo("A001A");
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

    @Test
    void shouldBuildMultiCodeClaim_totalingLoadedFees() {
        BillingOnClaimPersister persister = Mockito.mock(BillingOnClaimPersister.class);
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingSpecialistClaimService service = new BillingSpecialistClaimService(persister, serviceCodeLoader);
        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "A001A", "Minor assessment", "10.00", "0.00", "", "false")));
        when(serviceCodeLoader.getBillingCodeAttr("K013A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "K013A", "Counselling", "15.50", "0.00", "", "false")));

        BillingSpecialistClaim claim = service.buildBillingClaim(command("A001A, K013A"));

        assertThat(claim.header().getTotal()).isEqualTo("25.50");
        assertThat(claim.items()).extracting("serviceCode")
                .containsExactly("A001A", "K013A");
        assertThat(claim.items()).extracting("fee")
                .containsExactly("10.00", "15.50");
    }

    @Test
    void shouldPersistHeaderAndItems_whenClaimHasItems() {
        BillingOnClaimPersister persister = Mockito.mock(BillingOnClaimPersister.class);
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingSpecialistClaimService service = new BillingSpecialistClaimService(persister, serviceCodeLoader);
        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "A001A", "Minor assessment", "10.00", "0.00", "", "false")));
        when(persister.addOneClaimHeaderRecord(any())).thenReturn(12345);

        BillingSpecialistClaim claim = service.buildBillingClaim(command("A001A"));

        assertThat(service.addBillingRecord(claim)).isTrue();
        Mockito.verify(persister).addOneClaimHeaderRecord(claim.header());
        Mockito.verify(persister).addItemRecord(claim.items(), 12345);
    }

    @Test
    void shouldNotPersistItems_whenHeaderInsertFails() {
        BillingOnClaimPersister persister = Mockito.mock(BillingOnClaimPersister.class);
        ServiceCodeLoader serviceCodeLoader = Mockito.mock(ServiceCodeLoader.class);
        BillingSpecialistClaimService service = new BillingSpecialistClaimService(persister, serviceCodeLoader);
        when(serviceCodeLoader.getBillingCodeAttr("A001A"))
                .thenReturn(List.of(new io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute(
                        "A001A", "Minor assessment", "10.00", "0.00", "", "false")));
        when(persister.addOneClaimHeaderRecord(any())).thenReturn(0);

        BillingSpecialistClaim claim = service.buildBillingClaim(command("A001A"));

        assertThat(service.addBillingRecord(claim)).isFalse();
        Mockito.verify(persister).addOneClaimHeaderRecord(claim.header());
        Mockito.verify(persister, Mockito.never()).addItemRecord(any(), Mockito.anyInt());
    }

    private BillingSpecialistClaimCommand command(String serviceCodes) {
        return new BillingSpecialistClaimCommand(
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
                serviceCodes,
                "00",
                "250",
                "999998",
                "999998");
    }
}
