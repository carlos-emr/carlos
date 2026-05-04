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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Data carrier for {@code BillingOnStatusViewModelUnitTest}.
 *
 * <p>These classes carry legacy billing state between services, actions, and
 * JSPs. Prefer explicit fields and accessors here over loosely typed request
 * attributes in the view layer.</p>
 */

@DisplayName("BillingOnStatusViewModel")
@Tag("unit")
@Tag("billing")
class BillingOnStatusViewModelUnitTest {

    @Test
    void shouldExposeDefaultBillTypes_matchingLegacyScriptlet() {
        // The legacy JSP top scriptlet hard-coded:
        // {"HCP", "WCB", "RMB", "NOT", "PAT", "OCF", "ODS", "CPP", "STD", "IFH"}
        assertThat(BillingOnStatusViewModel.DEFAULT_BILL_TYPES)
                .containsExactly("HCP", "WCB", "RMB", "NOT", "PAT", "OCF", "ODS", "CPP", "STD", "IFH");
    }

    @Test
    void shouldRoundTripBuilderInput_forAllParamEchoFields() {
        BillingOnStatusViewModel m = BillingOnStatusViewModel.builder()
                .teamBillingOnly(true)
                .siteAccessPrivacy(true)
                .multisites(true)
                .hideName(true)
                .search(true)
                .billTypes(List.of("HCP", "WCB"))
                .statusType("O")
                .demoNo("1")
                .filename("1")
                .startDate("2025-10-26")
                .endDate("2026-04-24")
                .providerNo("999998")
                .providerOhipNo("OHIP1")
                .raCode("RA1")
                .claimNo("CLAIM1")
                .dx("401")
                .visitType("00")
                .serviceCode("A007A")
                .billingForm("GP")
                .visitLocation("0000")
                .selectedSite("site1")
                .sortName("ServiceDate")
                .sortOrder("asc")
                .paymentStartDate("2025-10-26")
                .paymentEndDate("2026-04-24")
                .build();

        assertThat(m.isTeamBillingOnly()).isTrue();
        assertThat(m.isSiteAccessPrivacy()).isTrue();
        assertThat(m.isMultisites()).isTrue();
        assertThat(m.isHideName()).isTrue();
        assertThat(m.isSearch()).isTrue();
        assertThat(m.getBillTypes()).containsExactly("HCP", "WCB");
        assertThat(m.getStatusType()).isEqualTo("O");
        assertThat(m.getDemoNo()).isEqualTo("1");
        assertThat(m.getFilename()).isEqualTo("1");
        assertThat(m.getStartDate()).isEqualTo("2025-10-26");
        assertThat(m.getEndDate()).isEqualTo("2026-04-24");
        assertThat(m.getProviderNo()).isEqualTo("999998");
        assertThat(m.getProviderOhipNo()).isEqualTo("OHIP1");
        assertThat(m.getRaCode()).isEqualTo("RA1");
        assertThat(m.getClaimNo()).isEqualTo("CLAIM1");
        assertThat(m.getDx()).isEqualTo("401");
        assertThat(m.getVisitType()).isEqualTo("00");
        assertThat(m.getServiceCode()).isEqualTo("A007A");
        assertThat(m.getBillingForm()).isEqualTo("GP");
        assertThat(m.getVisitLocation()).isEqualTo("0000");
        assertThat(m.getSelectedSite()).isEqualTo("site1");
        assertThat(m.getSortName()).isEqualTo("ServiceDate");
        assertThat(m.getSortOrder()).isEqualTo("asc");
        assertThat(m.getPaymentStartDate()).isEqualTo("2025-10-26");
        assertThat(m.getPaymentEndDate()).isEqualTo("2026-04-24");
    }

    @Test
    void shouldDefaultBillTypesToEmpty_whenNotSet() {
        BillingOnStatusViewModel m = BillingOnStatusViewModel.builder().build();
        assertThat(m.getBillTypes()).isEmpty();
    }

    @Test
    void shouldAcceptBillTypes_fromStringArrayOverload() {
        BillingOnStatusViewModel m = BillingOnStatusViewModel.builder()
                .billTypes(new String[]{"HCP", "PAT"})
                .build();
        assertThat(m.getBillTypes()).containsExactly("HCP", "PAT");
    }

    @Test
    void shouldDefaultPrimitiveBooleansToFalse_whenNotSet() {
        BillingOnStatusViewModel m = BillingOnStatusViewModel.builder().build();
        assertThat(m.isTeamBillingOnly()).isFalse();
        assertThat(m.isSiteAccessPrivacy()).isFalse();
        assertThat(m.isMultisites()).isFalse();
        assertThat(m.isHideName()).isFalse();
        assertThat(m.isSearch()).isFalse();
    }

    /**
     * Null-tolerance contract: every String setter must coalesce a passed
     * {@code null} to the empty string. EL output renders bare null as
     * the literal 4-character word "null" — guarding here means a future
     * caller forgetting to coalesce can't pollute the rendered page.
     */
    @Test
    void shouldCoalesceNullStringsToEmpty_acrossEverySetter() {
        BillingOnStatusViewModel m = BillingOnStatusViewModel.builder()
                .statusType(null)
                .providerNo(null)
                .providerOhipNo(null)
                .startDate(null)
                .endDate(null)
                .demoNo(null)
                .serviceCode(null)
                .raCode(null)
                .claimNo(null)
                .dx(null)
                .visitType(null)
                .filename(null)
                .selectedSite(null)
                .billingForm(null)
                .visitLocation(null)
                .sortName(null)
                .sortOrder(null)
                .paymentStartDate(null)
                .paymentEndDate(null)
                .build();

        assertThat(m.getStatusType()).isEmpty();
        assertThat(m.getProviderNo()).isEmpty();
        assertThat(m.getProviderOhipNo()).isEmpty();
        assertThat(m.getStartDate()).isEmpty();
        assertThat(m.getEndDate()).isEmpty();
        assertThat(m.getDemoNo()).isEmpty();
        assertThat(m.getServiceCode()).isEmpty();
        assertThat(m.getRaCode()).isEmpty();
        assertThat(m.getClaimNo()).isEmpty();
        assertThat(m.getDx()).isEmpty();
        assertThat(m.getVisitType()).isEmpty();
        assertThat(m.getFilename()).isEmpty();
        assertThat(m.getSelectedSite()).isEmpty();
        assertThat(m.getBillingForm()).isEmpty();
        assertThat(m.getVisitLocation()).isEmpty();
        assertThat(m.getSortName()).isEmpty();
        assertThat(m.getSortOrder()).isEmpty();
        assertThat(m.getPaymentStartDate()).isEmpty();
        assertThat(m.getPaymentEndDate()).isEmpty();
    }
}
