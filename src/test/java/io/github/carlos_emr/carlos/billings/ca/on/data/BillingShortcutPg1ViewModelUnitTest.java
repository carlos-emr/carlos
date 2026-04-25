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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingShortcutPg1ViewModel")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg1ViewModelUnitTest {

    @Test
    void shouldDefaultStringFieldsToEmpty_whenBuilderUnused() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder().build();

        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.getProviderView()).isEmpty();
        assertThat(m.getDemoNo()).isEmpty();
        assertThat(m.getDemoName()).isEmpty();
        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDxCode()).isEmpty();
        assertThat(m.getMsg()).isEmpty();
    }

    @Test
    void shouldDefaultCollectionsToEmpty_whenBuilderUnused() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder().build();

        assertThat(m.getBillingHistory()).isEmpty();
        assertThat(m.getBillingHistoryDetails()).isEmpty();
        assertThat(m.getProviders()).isEmpty();
        assertThat(m.getClinicLocations()).isEmpty();
        assertThat(m.getServiceCodeCol1()).isEmpty();
        assertThat(m.getServiceCodeCol2()).isEmpty();
        assertThat(m.getServiceCodeCol3()).isEmpty();
        assertThat(m.getPropPremium()).isEmpty();
    }

    @Test
    void shouldRoundTripIdentityAndDemographicFields_whenBuilderSetsThem() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .userProviderNo("999998")
                .providerView("999998")
                .demoNo("1")
                .demoName("Jacky,Jones")
                .apptNo("0")
                .apptProviderNo("999998")
                .apptDate("2026-04-24")
                .startTime("00:00:00")
                .ctlBillForm("GP")
                .clinicNo("1234")
                .demoFirst("Jones")
                .demoLast("Jacky")
                .demoSex("1")
                .demoHin("9876543225AB")
                .demoDob("19850615")
                .demoDobYy("1985")
                .demoDobMm("06")
                .demoDobDd("15")
                .demoHcType("ON")
                .assignedProviderNo("999998")
                .referralDoctorName("Smith,John")
                .referralDoctorOhip("123456")
                .visitType("02")
                .clinicView("1000")
                .visitDate("2026-04-24")
                .dxCode("401")
                .errorFlag("")
                .errorMessage("")
                .warningMessage("")
                .msg("")
                .build();

        assertThat(m.getUserProviderNo()).isEqualTo("999998");
        assertThat(m.getProviderView()).isEqualTo("999998");
        assertThat(m.getDemoNo()).isEqualTo("1");
        assertThat(m.getDemoName()).isEqualTo("Jacky,Jones");
        assertThat(m.getDemoFirst()).isEqualTo("Jones");
        assertThat(m.getDemoLast()).isEqualTo("Jacky");
        assertThat(m.getDemoHin()).isEqualTo("9876543225AB");
        assertThat(m.getDemoDob()).isEqualTo("19850615");
        assertThat(m.getDemoHcType()).isEqualTo("ON");
        assertThat(m.getReferralDoctorName()).isEqualTo("Smith,John");
        assertThat(m.getReferralDoctorOhip()).isEqualTo("123456");
        assertThat(m.getVisitType()).isEqualTo("02");
        assertThat(m.getDxCode()).isEqualTo("401");
        assertThat(m.getCtlBillForm()).isEqualTo("GP");
    }

    @Test
    void shouldReturnVectorViews_forLegacyJspIteration() {
        List<Properties> hist = new ArrayList<>();
        Properties p = new Properties();
        p.setProperty("billing_no", "1");
        p.setProperty("visitType", "00");
        hist.add(p);

        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .billingHistory(hist)
                .billingHistoryDetails(hist)
                .providers(hist)
                .clinicLocations(hist)
                .serviceCodeCol1(hist)
                .serviceCodeCol2(hist)
                .serviceCodeCol3(hist)
                .build();

        // Vector views are defensive copies; not the same instance, but same contents.
        assertThat(m.getBillingHistoryVec()).hasSize(1);
        assertThat(m.getBillingHistoryDetailsVec()).hasSize(1);
        assertThat(m.getProvidersVec()).hasSize(1);
        assertThat(m.getClinicLocationsVec()).hasSize(1);
        assertThat(m.getServiceCodeCol1Vec()).hasSize(1);
        assertThat(m.getServiceCodeCol2Vec()).hasSize(1);
        assertThat(m.getServiceCodeCol3Vec()).hasSize(1);
        assertThat(m.getBillingHistoryVec().get(0).getProperty("billing_no")).isEqualTo("1");
    }

    @Test
    void shouldExposePropPremium_asDefensivePropertiesCopy() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .propPremium(Map.of("A007A", "A", "A001A", "A"))
                .build();

        Properties props = m.getPropPremiumProps();
        assertThat(props.getProperty("A007A")).isEqualTo("A");
        assertThat(props.getProperty("A001A")).isEqualTo("A");
        // Mutations to the returned Properties don't propagate.
        props.setProperty("A007A", "X");
        assertThat(m.getPropPremiumProps().getProperty("A007A")).isEqualTo("A");
    }

    /**
     * Structured-record accessors mirror the flat getters — regression armor
     * so a future refactor that drops a flat getter breaks loud.
     */
    @Test
    void shouldExposeValidationMessagesAsRecord_mirroringFlatGetters() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .errorFlag("1")
                .errorMessage("<b>err</b>")
                .warningMessage("<i>warn</i>")
                .build();

        BillingValidationMessages msgs = m.getValidationMessages();
        assertThat(msgs.errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(msgs.errorMessage()).isEqualTo(m.getErrorMessage());
        assertThat(msgs.warningMessage()).isEqualTo(m.getWarningMessage());
    }

    @Test
    void shouldExposeDemographicSummaryAsRecord_mirroringFlatGetters() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .demoFirst("Jones")
                .demoLast("Jacky")
                .demoHin("9876543225")
                .demoSex("1")
                .demoHcType("ON")
                .demoDob("19850615")
                .demoDobYy("1985")
                .demoDobMm("06")
                .demoDobDd("15")
                .build();

        BillingDemographicSummary demo = m.getDemographicSummary();
        assertThat(demo.firstName()).isEqualTo("Jones");
        assertThat(demo.lastName()).isEqualTo("Jacky");
        assertThat(demo.hin()).isEqualTo("9876543225");
        // Shortcut doesn't carry a hin-version field — empty by design.
        assertThat(demo.ver()).isEmpty();
        assertThat(demo.sex()).isEqualTo("1");
        assertThat(demo.hcType()).isEqualTo("ON");
        assertThat(demo.dob()).isEqualTo("19850615");
        assertThat(demo.dobYy()).isEqualTo("1985");
        assertThat(demo.dobMm()).isEqualTo("06");
        assertThat(demo.dobDd()).isEqualTo("15");
    }

    @Test
    void shouldExposeReferralDoctorAsRecord_mirroringFlatGetters() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .referralDoctorName("Smith")
                .referralDoctorOhip("123456")
                .build();

        BillingReferralDoctor r = m.getReferralDoctorRecord();
        assertThat(r.name()).isEqualTo("Smith");
        assertThat(r.ohip()).isEqualTo("123456");
        // Shortcut doesn't carry a specialty field — empty by design.
        assertThat(r.specialty()).isEmpty();
    }
}
