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
    void shouldRoundTripBuilderInput() {
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
}
