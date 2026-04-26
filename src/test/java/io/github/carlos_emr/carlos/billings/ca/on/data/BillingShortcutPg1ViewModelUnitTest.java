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

        // Assert against the flat getters, not literal strings — that's
        // the actual mirror property the JavaDoc claims to test.
        BillingDemographicSummary demo = m.getDemographicSummary();
        assertThat(demo.firstName()).isEqualTo(m.getDemoFirst());
        assertThat(demo.lastName()).isEqualTo(m.getDemoLast());
        assertThat(demo.hin()).isEqualTo(m.getDemoHin());
        // Shortcut doesn't carry a hin-version field — empty by design.
        assertThat(demo.ver()).isEmpty();
        assertThat(demo.sex()).isEqualTo(m.getDemoSex());
        assertThat(demo.hcType()).isEqualTo(m.getDemoHcType());
        assertThat(demo.dob()).isEqualTo(m.getDemoDob());
        assertThat(demo.dobYy()).isEqualTo(m.getDemoDobYy());
        assertThat(demo.dobMm()).isEqualTo(m.getDemoDobMm());
        assertThat(demo.dobDd()).isEqualTo(m.getDemoDobDd());
    }

    @Test
    void shouldExposeReferralDoctorAsRecord_mirroringFlatGetters() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .referralDoctorName("Smith")
                .referralDoctorOhip("123456")
                .build();

        BillingReferralDoctor r = m.getReferralDoctorRecord();
        assertThat(r.name()).isEqualTo(m.getReferralDoctorName());
        assertThat(r.ohip()).isEqualTo(m.getReferralDoctorOhip());
        // Shortcut doesn't carry a specialty field — empty by design.
        assertThat(r.specialty()).isEmpty();
    }

    /**
     * Null-tolerance contract: every String setter must coalesce a passed
     * {@code null} to the empty string so EL output never renders the
     * literal 4-character word "null". A future caller forgetting to
     * coalesce can't pollute the rendered page.
     */
    @Test
    void shouldCoalesceNullStringsToEmpty_acrossEverySetter() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .userProviderNo(null)
                .providerView(null)
                .demoNo(null)
                .demoName(null)
                .apptNo(null)
                .apptProviderNo(null)
                .apptDate(null)
                .startTime(null)
                .ctlBillForm(null)
                .clinicNo(null)
                .demoFirst(null)
                .demoLast(null)
                .demoSex(null)
                .demoHin(null)
                .demoDob(null)
                .demoDobYy(null)
                .demoDobMm(null)
                .demoDobDd(null)
                .demoHcType(null)
                .assignedProviderNo(null)
                .referralDoctorName(null)
                .referralDoctorOhip(null)
                .visitType(null)
                .clinicView(null)
                .visitDate(null)
                .dxCode(null)
                .errorFlag(null)
                .errorMessage(null)
                .warningMessage(null)
                .msg(null)
                .build();

        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.getProviderView()).isEmpty();
        assertThat(m.getDemoNo()).isEmpty();
        assertThat(m.getDemoName()).isEmpty();
        assertThat(m.getApptNo()).isEmpty();
        assertThat(m.getApptProviderNo()).isEmpty();
        assertThat(m.getApptDate()).isEmpty();
        assertThat(m.getStartTime()).isEmpty();
        assertThat(m.getCtlBillForm()).isEmpty();
        assertThat(m.getClinicNo()).isEmpty();
        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDemoSex()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
        assertThat(m.getDemoDobYy()).isEmpty();
        assertThat(m.getDemoDobMm()).isEmpty();
        assertThat(m.getDemoDobDd()).isEmpty();
        assertThat(m.getDemoHcType()).isEmpty();
        assertThat(m.getAssignedProviderNo()).isEmpty();
        assertThat(m.getReferralDoctorName()).isEmpty();
        assertThat(m.getReferralDoctorOhip()).isEmpty();
        assertThat(m.getVisitType()).isEmpty();
        assertThat(m.getClinicView()).isEmpty();
        assertThat(m.getVisitDate()).isEmpty();
        assertThat(m.getDxCode()).isEmpty();
        assertThat(m.getErrorFlag()).isEmpty();
        assertThat(m.getErrorMessage()).isEmpty();
        assertThat(m.getWarningMessage()).isEmpty();
        assertThat(m.getMsg()).isEmpty();
    }

    @Test
    @DisplayName("composed accessors expose the same data as flat getters")
    void shouldExposeComposed_fromFlatSetters() {
        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .demoFirst("Jane").demoLast("Doe").demoHin("9876543225")
                .demoSex("F").demoHcType("ON")
                .demoDob("19850615").demoDobYy("1985").demoDobMm("06").demoDobDd("15")
                .referralDoctorName("Smith").referralDoctorOhip("123456")
                .errorFlag("1").errorMessage("err").warningMessage("warn")
                .rmaEnabled(true)
                .selectedClinicNbrPrefix("XX")
                .build();

        // Composed records mirror flat values.
        assertThat(m.getDemographic().firstName()).isEqualTo(m.getDemoFirst());
        assertThat(m.getDemographic().lastName()).isEqualTo(m.getDemoLast());
        assertThat(m.getDemographic().hin()).isEqualTo(m.getDemoHin());
        // Shortcut never populates ver — composed slot stays empty per design.
        assertThat(m.getDemographic().ver()).isEmpty();
        assertThat(m.getReferral().name()).isEqualTo(m.getReferralDoctorName());
        assertThat(m.getReferral().ohip()).isEqualTo(m.getReferralDoctorOhip());
        assertThat(m.getMessagesAggregate().errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(m.getMessagesAggregate().errorMessage()).isEqualTo(m.getErrorMessage());
        assertThat(m.getMessagesAggregate().warningMessage()).isEqualTo(m.getWarningMessage());
        assertThat(m.getMultisite().rmaEnabled()).isEqualTo(m.isRmaEnabled());
        assertThat(m.getMultisite().selectedClinicNbrPrefix()).isEqualTo(m.getSelectedClinicNbrPrefix());
    }

    @Test
    @DisplayName("composed setter wins over flat setters")
    void shouldDelegateFlatGetters_toComposedRecord_whenComposedSetterUsed() {
        BillingDemographicSummary demoComposed = new BillingDemographicSummary(
                "Composed", "Doe", "9999999999", "", "1", "ON",
                "19900101", "1990", "01", "01");
        BillingReferralDoctor refComposed = new BillingReferralDoctor(
                "Smith,Adam", "987654", "GP");

        BillingShortcutPg1ViewModel m = BillingShortcutPg1ViewModel.builder()
                .demoFirst("Flat").demoLast("Wrong")
                .referralDoctorName("Wrong").referralDoctorOhip("000000")
                .demographic(demoComposed)
                .referral(refComposed)
                .build();

        assertThat(m.getDemographic()).isSameAs(demoComposed);
        assertThat(m.getReferral()).isSameAs(refComposed);
        assertThat(m.getDemoFirst()).isEqualTo("Composed");
        assertThat(m.getDemoLast()).isEqualTo("Doe");
        assertThat(m.getReferralDoctorName()).isEqualTo("Smith,Adam");
    }
}
