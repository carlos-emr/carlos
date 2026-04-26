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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingONReviewViewModel")
@Tag("unit")
@Tag("billing")
class BillingONReviewViewModelUnitTest {

    @Test
    void shouldReturnDefaults_whenBuiltWithNoFieldsSet() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder().build();

        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDxCode()).isEmpty();
        assertThat(m.getErrorFlag()).isEmpty();
        assertThat(m.getErrorMessage()).isEmpty();
        assertThat(m.getWarningMessage()).isEmpty();
    }

    @Test
    void shouldRoundTripBuilderInput_forAllSetters() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .demoFirst("Jones")
                .demoLast("Jacky")
                .demoHin("9876543225")
                .demoVer("AB")
                .demoSex("1")
                .demoHcType("ON")
                .demoDob("19850615")
                .demoDobYy("1985")
                .demoDobMm("06")
                .demoDobDd("15")
                .patientAddress("Jones Jacky\n101 Main\nToronto, Ontario\nM1A 1A1\nTel: 555-555-5555")
                .referralDoctorName("Smith")
                .referralDoctorOhip("123456")
                .assignedProviderNo("999998")
                .providerOhip("OHIP1")
                .providerRma("RMA1")
                .providerView("999998")
                .dxCode("401")
                .dxDesc("Essential, benign hypertension")
                .errorFlag("1")
                .errorMessage("<br><div>err</div>")
                .warningMessage("<br><div>warn</div>")
                .build();

        assertThat(m.getDemoFirst()).isEqualTo("Jones");
        assertThat(m.getDemoLast()).isEqualTo("Jacky");
        assertThat(m.getDemoHin()).isEqualTo("9876543225");
        assertThat(m.getDemoVer()).isEqualTo("AB");
        assertThat(m.getDemoSex()).isEqualTo("1");
        assertThat(m.getDemoHcType()).isEqualTo("ON");
        assertThat(m.getDemoDob()).isEqualTo("19850615");
        assertThat(m.getDemoDobYy()).isEqualTo("1985");
        assertThat(m.getDemoDobMm()).isEqualTo("06");
        assertThat(m.getDemoDobDd()).isEqualTo("15");
        assertThat(m.getPatientAddress()).contains("Jacky").contains("Toronto");
        assertThat(m.getReferralDoctorName()).isEqualTo("Smith");
        assertThat(m.getReferralDoctorOhip()).isEqualTo("123456");
        assertThat(m.getAssignedProviderNo()).isEqualTo("999998");
        assertThat(m.getProviderOhip()).isEqualTo("OHIP1");
        assertThat(m.getProviderRma()).isEqualTo("RMA1");
        assertThat(m.getProviderView()).isEqualTo("999998");
        assertThat(m.getDxCode()).isEqualTo("401");
        assertThat(m.getDxDesc()).isEqualTo("Essential, benign hypertension");
        assertThat(m.getErrorFlag()).isEqualTo("1");
        assertThat(m.getErrorMessage()).contains("err");
        assertThat(m.getWarningMessage()).contains("warn");
    }

    @Test
    void shouldDefaultErrorAndWarningToEmpty_whenOnlyDemographicFieldsSet() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .demoFirst("Jones")
                .demoLast("Jacky")
                .dxCode("401")
                .build();

        assertThat(m.getErrorMessage()).isEmpty();
        assertThat(m.getWarningMessage()).isEmpty();
        assertThat(m.getErrorFlag()).isEmpty();
    }

    /**
     * Null-tolerance contract: every String setter must coalesce a passed
     * {@code null} to the empty string, so the JSP never renders the
     * literal 4-character word "null" in EL output. Mirrors the contract
     * already enforced in {@link BillingONCorrectionViewModel} and
     * {@link BillingShortcutPg1ViewModel}.
     */
    @Test
    void shouldCoalesceNullStringsToEmpty_acrossEverySetter() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .demoFirst(null)
                .demoLast(null)
                .demoHin(null)
                .demoVer(null)
                .demoSex(null)
                .demoHcType(null)
                .demoDob(null)
                .demoDobYy(null)
                .demoDobMm(null)
                .demoDobDd(null)
                .patientAddress(null)
                .referralDoctorName(null)
                .referralDoctorOhip(null)
                .assignedProviderNo(null)
                .providerOhip(null)
                .providerRma(null)
                .providerView(null)
                .dxCode(null)
                .dxDesc(null)
                .errorFlag(null)
                .errorMessage(null)
                .warningMessage(null)
                .build();

        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoVer()).isEmpty();
        assertThat(m.getDemoSex()).isEmpty();
        assertThat(m.getDemoHcType()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
        assertThat(m.getDemoDobYy()).isEmpty();
        assertThat(m.getDemoDobMm()).isEmpty();
        assertThat(m.getDemoDobDd()).isEmpty();
        assertThat(m.getPatientAddress()).isEmpty();
        assertThat(m.getReferralDoctorName()).isEmpty();
        assertThat(m.getReferralDoctorOhip()).isEmpty();
        assertThat(m.getAssignedProviderNo()).isEmpty();
        assertThat(m.getProviderOhip()).isEmpty();
        assertThat(m.getProviderRma()).isEmpty();
        assertThat(m.getProviderView()).isEmpty();
        assertThat(m.getDxCode()).isEmpty();
        assertThat(m.getDxDesc()).isEmpty();
        assertThat(m.getErrorFlag()).isEmpty();
        assertThat(m.getErrorMessage()).isEmpty();
        assertThat(m.getWarningMessage()).isEmpty();
    }

    /**
     * Structured-record accessors mirror the flat getters — regression armor
     * so a future refactor that drops a flat getter breaks loud.
     */
    @Test
    void shouldExposeValidationMessagesAsRecord_mirroringFlatGetters() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .errorFlag("1")
                .errorMessage("<b>err</b>")
                .warningMessage("<i>warn</i>")
                .build();

        BillingValidationMessages msgs = m.getValidationMessagesAggregate();
        assertThat(msgs.errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(msgs.errorMessage()).isEqualTo(m.getErrorMessage());
        assertThat(msgs.warningMessage()).isEqualTo(m.getWarningMessage());
    }

    @Test
    void shouldExposeDemographicSummaryAsRecord_mirroringFlatGetters() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .demoFirst("Jones")
                .demoLast("Jacky")
                .demoHin("9876543225")
                .demoVer("AB")
                .demoSex("1")
                .demoHcType("ON")
                .demoDob("19850615")
                .demoDobYy("1985")
                .demoDobMm("06")
                .demoDobDd("15")
                .build();

        // Assert against the flat getters, not literal strings — that's
        // the actual mirror property the JavaDoc claims to test.
        BillingDemographicSummary demo = m.getDemographicSummary();
        assertThat(demo.firstName()).isEqualTo(m.getDemoFirst());
        assertThat(demo.lastName()).isEqualTo(m.getDemoLast());
        assertThat(demo.hin()).isEqualTo(m.getDemoHin());
        assertThat(demo.ver()).isEqualTo(m.getDemoVer());
        assertThat(demo.sex()).isEqualTo(m.getDemoSex());
        assertThat(demo.hcType()).isEqualTo(m.getDemoHcType());
        assertThat(demo.dob()).isEqualTo(m.getDemoDob());
        assertThat(demo.dobYy()).isEqualTo(m.getDemoDobYy());
        assertThat(demo.dobMm()).isEqualTo(m.getDemoDobMm());
        assertThat(demo.dobDd()).isEqualTo(m.getDemoDobDd());
    }

    @Test
    void shouldExposeReferralDoctorAsRecord_mirroringFlatGetters() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .referralDoctorName("Smith")
                .referralDoctorOhip("123456")
                .build();

        BillingReferralDoctor r = m.getReferralDoctorRecord();
        assertThat(r.name()).isEqualTo(m.getReferralDoctorName());
        assertThat(r.ohip()).isEqualTo(m.getReferralDoctorOhip());
        // Review doesn't carry a specialty field — empty by design.
        assertThat(r.specialty()).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("composed accessors expose the same data as flat getters")
    void shouldExposeComposed_fromFlatSetters() {
        BillingONReviewViewModel m = BillingONReviewViewModel.builder()
                .demoFirst("Jane").demoLast("Doe").demoHin("9876543225").demoVer("AB")
                .demoSex("2").demoHcType("ON")
                .demoDob("19850615").demoDobYy("1985").demoDobMm("06").demoDobDd("15")
                .referralDoctorName("Smith").referralDoctorOhip("123456")
                .errorFlag("1").errorMessage("err").warningMessage("warn")
                .multisitesEnabled(true)
                .build();

        // The composed records reflect the same data as the flat getters.
        assertThat(m.getDemographic().firstName()).isEqualTo(m.getDemoFirst());
        assertThat(m.getDemographic().lastName()).isEqualTo(m.getDemoLast());
        assertThat(m.getReferral().name()).isEqualTo(m.getReferralDoctorName());
        assertThat(m.getReferral().ohip()).isEqualTo(m.getReferralDoctorOhip());
        assertThat(m.getMessages().errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(m.getMessages().errorMessage()).isEqualTo(m.getErrorMessage());
        assertThat(m.getMessages().warningMessage()).isEqualTo(m.getWarningMessage());
        assertThat(m.getMultisite().enabled()).isEqualTo(m.isMultisitesEnabled());
    }
}
