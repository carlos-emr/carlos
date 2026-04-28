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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Data carrier for {@code BillingONCorrectionViewModelUnitTest}.
 *
 * <p>These classes carry legacy billing state between services, actions, and
 * JSPs. Prefer explicit fields and accessors here over loosely typed request
 * attributes in the view layer.</p>
 */

@DisplayName("BillingONCorrectionViewModel")
@Tag("unit")
@Tag("billing")
class BillingONCorrectionViewModelUnitTest {

    @Test
    void shouldDefaultAllFields_whenBuiltEmpty() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder().build();

        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.getUserFirstName()).isEmpty();
        assertThat(m.getUserLastName()).isEmpty();
        assertThat(m.isSiteAccessPrivacy()).isFalse();
        assertThat(m.isTeamAccessPrivacy()).isFalse();
        assertThat(m.isTeamBillingOnly()).isFalse();
        assertThat(m.isMultisites()).isFalse();
        assertThat(m.getProviderAccessList()).isEmpty();
        assertThat(m.getMgrSites()).isEmpty();
    }

    @Test
    void shouldRoundTripAllFields_whenBuilderSetsThem() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .userProviderNo("999998")
                .userFirstName("doctor")
                .userLastName("carlosdoc")
                .siteAccessPrivacy(true)
                .teamAccessPrivacy(true)
                .teamBillingOnly(true)
                .multisites(true)
                .providerAccessList(Set.of("999998", "111111"))
                .mgrSites(List.of("Site A", "Site B"))
                .build();

        assertThat(m.getUserProviderNo()).isEqualTo("999998");
        assertThat(m.getUserFirstName()).isEqualTo("doctor");
        assertThat(m.getUserLastName()).isEqualTo("carlosdoc");
        assertThat(m.isSiteAccessPrivacy()).isTrue();
        assertThat(m.isTeamAccessPrivacy()).isTrue();
        assertThat(m.isTeamBillingOnly()).isTrue();
        assertThat(m.isMultisites()).isTrue();
        assertThat(m.getProviderAccessList()).containsExactlyInAnyOrder("999998", "111111");
        assertThat(m.getMgrSites()).containsExactly("Site A", "Site B");
    }

    @Test
    void shouldReturnImmutableProviderAccessList_whenAccessed() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .providerAccessList(Set.of("999998"))
                .build();

        assertThatThrownBy(() -> m.getProviderAccessList().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnImmutableMgrSites_whenAccessed() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .mgrSites(List.of("Site A"))
                .build();

        assertThatThrownBy(() -> m.getMgrSites().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Regression armor for the d2db61d4 Group D bridge-migration bug where
     * `xml_payProgram` was bound to `correctionModel.billDate` (copy/paste
     * error). The model fields are independent — verify they round-trip
     * separately so a future renaming or accidental field merge breaks loud.
     */
    @Test
    void shouldKeepPayProgram_independentFromBillDate() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .payProgram("HCP")
                .billDate("2026-04-25")
                .build();

        assertThat(m.getPayProgram()).isEqualTo("HCP");
        assertThat(m.getBillDate()).isEqualTo("2026-04-25");
        assertThat(m.getPayProgram()).isNotEqualTo(m.getBillDate());
    }

    /**
     * Regression armor for the d2db61d4 clinic-dropdown bug where every
     * `<option>` rendered the same `correctionModel.billLocationNo` instead
     * of the per-iteration loop variable. The model field is a single
     * canonical String value, distinct from the `bCh1.getFaciltyNum()` row
     * data — verify the field exists and round-trips.
     */
    @Test
    void shouldRoundTripBillLocationNo_asASingleCanonicalValue() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .billLocationNo("1985")
                .build();

        assertThat(m.getBillLocationNo()).isEqualTo("1985");
    }

    /**
     * The bill-record fields (billLocationNo, payProgram, billDate, billStatus,
     * billProvider, billTotal, hcType, hcSex) are populated by
     * BillingCorrection2Action#loadBillRecord from a BillingONCHeader1 row.
     * They MUST be independent — collapsing any pair would re-introduce the
     * d2db61d4 bridge-migration class of bugs.
     */
    /**
     * Regression armor for the multiSiteProvider fail-closed default. The JSP
     * only consumes isMultiSiteProvider() when isBillLoaded() is true; on the
     * empty-fallback render path the value MUST default to false so an
     * unloaded model doesn't silently grant unrestricted multi-site access.
     * The assembler explicitly opts in to true via .multiSiteProvider(true)
     * when the bill loads and the access checks pass.
     */
    @Test
    @Tag("security")
    void shouldDefaultMultiSiteProviderToFalse_whenBuilderIsEmpty() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder().build();

        assertThat(m.isMultiSiteProvider()).isFalse();
    }

    @Test
    void shouldRoundTripMultiSiteProvider_whenBuilderSetsTrue() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .multiSiteProvider(true)
                .build();

        assertThat(m.isMultiSiteProvider()).isTrue();
    }

    @Test
    void shouldKeepBillRecordFields_independentOfEachOther() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .billLocationNo("1985")
                .billDate("2026-04-25")
                .billStatus("O")
                .billProvider("999998")
                .billTotal("123.45")
                .payProgram("HCP")
                .hcType("ON")
                .hcSex("F")
                .build();

        assertThat(m.getBillLocationNo()).isEqualTo("1985");
        assertThat(m.getBillDate()).isEqualTo("2026-04-25");
        assertThat(m.getBillStatus()).isEqualTo("O");
        assertThat(m.getBillProvider()).isEqualTo("999998");
        assertThat(m.getBillTotal()).isEqualTo("123.45");
        assertThat(m.getPayProgram()).isEqualTo("HCP");
        assertThat(m.getHcType()).isEqualTo("ON");
        assertThat(m.getHcSex()).isEqualTo("F");
    }

    /**
     * Null-tolerance contract: every String setter must coalesce a passed
     * {@code null} to the empty string. EL output renders bare null as the
     * literal 4-character word "null" — guarding here means a future caller
     * forgetting to coalesce can't pollute the rendered page.
     */
    @Test
    void shouldCoalesceNullStringsToEmpty_acrossEverySetter() {
        BillingONCorrectionViewModel m = BillingONCorrectionViewModel.builder()
                .userProviderNo(null)
                .userFirstName(null)
                .userLastName(null)
                .billingNo(null)
                .claimNo(null)
                .createTimestamp(null)
                .demoNo(null)
                .demoName(null)
                .demoDob(null)
                .demoSex(null)
                .demoRosterStatus(null)
                .hin(null)
                .hcType(null)
                .hcSex(null)
                .billLocationNo(null)
                .billDate(null)
                .billProvider(null)
                .billStatus(null)
                .payProgram(null)
                .billTotal(null)
                .visitDate(null)
                .visitType(null)
                .sliCode(null)
                .referralDoctorOhip(null)
                .referralDoctor(null)
                .manReview(null)
                .comment(null)
                .clinicSite(null)
                .build();

        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.getUserFirstName()).isEmpty();
        assertThat(m.getUserLastName()).isEmpty();
        assertThat(m.getBillingNo()).isEmpty();
        assertThat(m.getClaimNo()).isEmpty();
        assertThat(m.getCreateTimestamp()).isEmpty();
        assertThat(m.getDemoNo()).isEmpty();
        assertThat(m.getDemoName()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
        assertThat(m.getDemoSex()).isEmpty();
        assertThat(m.getDemoRosterStatus()).isEmpty();
        assertThat(m.getHin()).isEmpty();
        assertThat(m.getHcType()).isEmpty();
        assertThat(m.getHcSex()).isEmpty();
        assertThat(m.getBillLocationNo()).isEmpty();
        assertThat(m.getBillDate()).isEmpty();
        assertThat(m.getBillProvider()).isEmpty();
        assertThat(m.getBillStatus()).isEmpty();
        assertThat(m.getPayProgram()).isEmpty();
        assertThat(m.getBillTotal()).isEmpty();
        assertThat(m.getVisitDate()).isEmpty();
        assertThat(m.getVisitType()).isEmpty();
        assertThat(m.getSliCode()).isEmpty();
        assertThat(m.getReferralDoctorOhip()).isEmpty();
        assertThat(m.getReferralDoctor()).isEmpty();
        assertThat(m.getManReview()).isEmpty();
        assertThat(m.getComment()).isEmpty();
        assertThat(m.getClinicSite()).isEmpty();
    }
}
