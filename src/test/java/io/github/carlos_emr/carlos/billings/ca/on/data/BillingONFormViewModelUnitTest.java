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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingONFormViewModel} builder round-trip + empty/defensive-copy semantics.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingONFormViewModel")
@Tag("unit")
@Tag("billing")
class BillingONFormViewModelUnitTest {

    @Test
    void shouldExposeBuilderFields_throughGetters() {
        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .userNo("doc1")
                .demographicNo("12345")
                .appointmentNo("999")
                .providerNo("doc1")
                .apptProviderNo("doc1")
                .providerView("doc1|1234567")
                .demoName("Doe,Jane")
                .today("2026-04-24")
                .billReferenceDate("2026-04-24")
                .mReview("1")
                .ctlBillForm("OFC")
                .curBillForm("OFC")
                .demoLast("Doe")
                .demoFirst("Jane")
                .demoHin("1234567890")
                .demoVer("AB")
                .demoDob("19800101")
                .demoDobYear("1980")
                .demoDobMonth("01")
                .demoDobDay("01")
                .demoHcType("ON")
                .demoSex("2")
                .familyDoctor("<rd>Smith</rd><rdohip>123456</rdohip>")
                .rosterStatus("RO")
                .assgProviderNo("doc2")
                .age(46)
                .referralDoctor("Smith,John")
                .referralDoctorOhip("123456")
                .referralSpecialty("GP")
                .warningMsg("some warning")
                .errorMsg("")
                .errorFlag("")
                .clinicView("0000")
                .clinicNo("1234")
                .visitType("03")
                .singleClickEnabled(true)
                .hospitalBilling(false)
                .defaultServiceType("OFC")
                .dxCode("401")
                .xmlVisitType("03")
                .xmlLocation("0000")
                .visitDate("")
                .defaultBillFormName("Office")
                .defaultBillType("HCP")
                .build();

        assertThat(model.getUserNo()).isEqualTo("doc1");
        assertThat(model.getDemographicNo()).isEqualTo("12345");
        assertThat(model.getAppointmentNo()).isEqualTo("999");
        assertThat(model.getProviderNo()).isEqualTo("doc1");
        assertThat(model.getApptProviderNo()).isEqualTo("doc1");
        assertThat(model.getProviderView()).isEqualTo("doc1|1234567");
        assertThat(model.getDemoName()).isEqualTo("Doe,Jane");
        assertThat(model.getToday()).isEqualTo("2026-04-24");
        assertThat(model.getBillReferenceDate()).isEqualTo("2026-04-24");
        assertThat(model.getMReview()).isEqualTo("1");
        assertThat(model.getCtlBillForm()).isEqualTo("OFC");
        assertThat(model.getCurBillForm()).isEqualTo("OFC");
        assertThat(model.getDemoLast()).isEqualTo("Doe");
        assertThat(model.getDemoFirst()).isEqualTo("Jane");
        assertThat(model.getDemoHin()).isEqualTo("1234567890");
        assertThat(model.getDemoVer()).isEqualTo("AB");
        assertThat(model.getDemoDob()).isEqualTo("19800101");
        assertThat(model.getDemoDobYear()).isEqualTo("1980");
        assertThat(model.getDemoDobMonth()).isEqualTo("01");
        assertThat(model.getDemoDobDay()).isEqualTo("01");
        assertThat(model.getDemoHcType()).isEqualTo("ON");
        assertThat(model.getDemoSex()).isEqualTo("2");
        assertThat(model.getFamilyDoctor()).isEqualTo("<rd>Smith</rd><rdohip>123456</rdohip>");
        assertThat(model.getRosterStatus()).isEqualTo("RO");
        assertThat(model.getAssgProviderNo()).isEqualTo("doc2");
        assertThat(model.getAge()).isEqualTo(46);
        assertThat(model.getReferralDoctor()).isEqualTo("Smith,John");
        assertThat(model.getReferralDoctorOhip()).isEqualTo("123456");
        assertThat(model.getReferralSpecialty()).isEqualTo("GP");
        assertThat(model.getWarningMsg()).isEqualTo("some warning");
        assertThat(model.getErrorMsg()).isEqualTo("");
        assertThat(model.getErrorFlag()).isEqualTo("");
        assertThat(model.getClinicView()).isEqualTo("0000");
        assertThat(model.getClinicNo()).isEqualTo("1234");
        assertThat(model.getVisitType()).isEqualTo("03");
        assertThat(model.isSingleClickEnabled()).isTrue();
        assertThat(model.isHospitalBilling()).isFalse();
        assertThat(model.getDefaultServiceType()).isEqualTo("OFC");
        assertThat(model.getDxCode()).isEqualTo("401");
        assertThat(model.getXmlVisitType()).isEqualTo("03");
        assertThat(model.getXmlLocation()).isEqualTo("0000");
        assertThat(model.getVisitDate()).isEqualTo("");
        assertThat(model.getDefaultBillFormName()).isEqualTo("Office");
        assertThat(model.getDefaultBillType()).isEqualTo("HCP");
    }

    @Test
    void shouldReturnEmptyCollections_whenCollectionsNotSet() {
        BillingONFormViewModel model = BillingONFormViewModel.builder().build();

        assertThat(model.getPatientDx()).isEmpty();
        assertThat(model.getBillingHistory()).isEmpty();
        assertThat(model.getProviders()).isEmpty();
        assertThat(model.getBillingServiceCodesMap()).isEmpty();
        assertThat(model.getListServiceType()).isEmpty();
        assertThat(model.getTitleMap()).isEmpty();
        assertThat(model.getPremiumCodes()).isEmpty();
        assertThat(model.getBillingForms()).isEmpty();
        assertThat(model.getDxCodesByServiceType()).isEmpty();
        assertThat(model.getBillingFavourites()).isEmpty();
        assertThat(model.getRequestEchoes()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyCollections_soMutatingTheBuilderInputDoesNotAffectTheModel() {
        java.util.ArrayList<String> dxList = new java.util.ArrayList<>();
        dxList.add("401");
        dxList.add("789");

        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .patientDx(dxList)
                .build();

        dxList.add("INJECTED-AFTER-BUILD");

        assertThat(model.getPatientDx()).containsExactly("401", "789");
    }

    @Test
    void shouldStoreProvidersRecord_asImmutableList() {
        List<BillingONFormViewModel.ProviderOption> providers = List.of(
                new BillingONFormViewModel.ProviderOption("Alpha", "Ada", "doc1|111111"),
                new BillingONFormViewModel.ProviderOption("Beta", "Bob", "doc2|222222"));

        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .providers(providers)
                .build();

        assertThat(model.getProviders()).hasSize(2);
        assertThat(model.getProviders().get(0).lastName()).isEqualTo("Alpha");
        assertThat(model.getProviders().get(0).proOhip()).isEqualTo("doc1|111111");
    }

    @Test
    void shouldStoreBillingHistory_usingTheRecordShape() {
        List<BillingONFormViewModel.BillingHistoryEntry> history = List.of(
                new BillingONFormViewModel.BillingHistoryEntry(
                        "2026-04-20", "03", "0000", "401"));

        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .billingHistory(history)
                .build();

        assertThat(model.getBillingHistory()).hasSize(1);
        BillingONFormViewModel.BillingHistoryEntry entry = model.getBillingHistory().get(0);
        assertThat(entry.visitDate()).isEqualTo("2026-04-20");
        assertThat(entry.visitType()).isEqualTo("03");
        assertThat(entry.clinicRefCode()).isEqualTo("0000");
        assertThat(entry.diagnosticCode()).isEqualTo("401");
    }

    @Test
    void shouldStoreServiceCodeGrid_asMapOfRecordLists() {
        BillingONFormViewModel.ServiceCodeEntry code = new BillingONFormViewModel.ServiceCodeEntry(
                "A001", "Consultation", "75.00", "100",
                "OFC", "Office", "", false);
        Map<String, List<BillingONFormViewModel.ServiceCodeEntry>> grid = Map.of(
                "group1_OFC", List.of(code));

        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .billingServiceCodesMap(grid)
                .listServiceType(List.of("OFC"))
                .titleMap(Map.of("group1_OFC", "Office Services"))
                .premiumCodes(Set.of("A001"))
                .build();

        assertThat(model.getBillingServiceCodesMap())
                .containsKey("group1_OFC")
                .extractingByKey("group1_OFC")
                .satisfies(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).serviceCode()).isEqualTo("A001");
                    assertThat(list.get(0).sliFlag()).isFalse();
                });
        assertThat(model.getListServiceType()).containsExactly("OFC");
        assertThat(model.getTitleMap()).containsEntry("group1_OFC", "Office Services");
        assertThat(model.getPremiumCodes()).containsExactly("A001");
    }

    @Test
    void shouldStoreBillingFormMenuAndDxCodePanels_asImmutableRecords() {
        BillingONFormViewModel model = BillingONFormViewModel.builder()
                .billingForms(List.of(new BillingONFormViewModel.BillingFormMenuEntry(
                        "OFC", "Office", "HCP")))
                .dxCodesByServiceType(Map.of("OFC",
                        List.of(new BillingONFormViewModel.DxCodeEntry(
                                "OFC", "401", "Essential Hypertension"))))
                .billingFavourites(List.of("combo1", "Fav Combo"))
                .build();

        assertThat(model.getBillingForms()).hasSize(1);
        assertThat(model.getBillingForms().get(0).code()).isEqualTo("OFC");
        assertThat(model.getBillingForms().get(0).billType()).isEqualTo("HCP");
        assertThat(model.getDxCodesByServiceType()).containsKey("OFC");
        assertThat(model.getDxCodesByServiceType().get("OFC")).hasSize(1);
        assertThat(model.getBillingFavourites()).containsExactly("combo1", "Fav Combo");
    }

    /**
     * Null-tolerance contract: every String setter must coalesce a passed
     * {@code null} to the empty string. EL output renders bare null as the
     * literal 4-character word "null". A representative subset of the ~30
     * String fields is exercised — the {@code nullToEmpty} call is uniform
     * across every field, so per-field coverage isn't required.
     */
    @Test
    void shouldCoalesceNullStringsToEmpty_acrossRepresentativeSetters() {
        BillingONFormViewModel m = BillingONFormViewModel.builder()
                .userNo(null)
                .demographicNo(null)
                .providerView(null)
                .demoName(null)
                .demoFirst(null)
                .demoLast(null)
                .demoHin(null)
                .demoVer(null)
                .demoDob(null)
                .demoHcType(null)
                .demoSex(null)
                .referralDoctor(null)
                .referralDoctorOhip(null)
                .errorMsg(null)
                .warningMsg(null)
                .errorFlag(null)
                .clinicView(null)
                .clinicNo(null)
                .visitType(null)
                .dxCode(null)
                .build();

        assertThat(m.getUserNo()).isEmpty();
        assertThat(m.getDemographicNo()).isEmpty();
        assertThat(m.getProviderView()).isEmpty();
        assertThat(m.getDemoName()).isEmpty();
        assertThat(m.getDemoFirst()).isEmpty();
        assertThat(m.getDemoLast()).isEmpty();
        assertThat(m.getDemoHin()).isEmpty();
        assertThat(m.getDemoVer()).isEmpty();
        assertThat(m.getDemoDob()).isEmpty();
        assertThat(m.getDemoHcType()).isEmpty();
        assertThat(m.getDemoSex()).isEmpty();
        assertThat(m.getReferralDoctor()).isEmpty();
        assertThat(m.getReferralDoctorOhip()).isEmpty();
        assertThat(m.getErrorMsg()).isEmpty();
        assertThat(m.getWarningMsg()).isEmpty();
        assertThat(m.getErrorFlag()).isEmpty();
        assertThat(m.getClinicView()).isEmpty();
        assertThat(m.getClinicNo()).isEmpty();
        assertThat(m.getVisitType()).isEmpty();
        assertThat(m.getDxCode()).isEmpty();
    }

    /**
     * Round-trip the demoDobInvalid flag (S3 — surfaces a banner on the form
     * when the patient's stored DOB is unparseable). Default must be false
     * so an unset model doesn't render a misleading banner.
     */
    @Test
    void shouldRoundTripDemoDobInvalid_throughBuilder() {
        BillingONFormViewModel defaultModel = BillingONFormViewModel.builder().build();
        assertThat(defaultModel.isDemoDobInvalid()).isFalse();

        BillingONFormViewModel flagged = BillingONFormViewModel.builder()
                .demoDobInvalid(true)
                .build();
        assertThat(flagged.isDemoDobInvalid()).isTrue();
    }

    /**
     * The structured-record accessors mirror the corresponding flat getters.
     * Adding regression armor so a future refactor that drops a flat getter
     * (or vice versa) breaks loud.
     */
    @Test
    void shouldExposeValidationMessagesAsRecord_mirroringFlatGetters() {
        BillingONFormViewModel m = BillingONFormViewModel.builder()
                .errorFlag("1")
                .errorMsg("<b>Error: bad DOB</b>")
                .warningMsg("<i>Warning: missing HIN</i>")
                .build();

        BillingValidationMessages msgs = m.getValidationMessages();
        assertThat(msgs.errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(msgs.errorMessage()).isEqualTo(m.getErrorMsg());
        assertThat(msgs.warningMessage()).isEqualTo(m.getWarningMsg());
        assertThat(msgs.hasError()).isTrue();
    }

    @Test
    void shouldExposeDemographicSummaryAsRecord_mirroringFlatGetters() {
        BillingONFormViewModel m = BillingONFormViewModel.builder()
                .demoFirst("Jane")
                .demoLast("Doe")
                .demoHin("9876543225")
                .demoVer("AB")
                .demoSex("F")
                .demoHcType("ON")
                .demoDob("19850615")
                .demoDobYear("1985")
                .demoDobMonth("06")
                .demoDobDay("15")
                .build();

        BillingDemographicSummary demo = m.getDemographicSummary();
        assertThat(demo.firstName()).isEqualTo("Jane");
        assertThat(demo.lastName()).isEqualTo("Doe");
        assertThat(demo.hin()).isEqualTo("9876543225");
        assertThat(demo.ver()).isEqualTo("AB");
        assertThat(demo.sex()).isEqualTo("F");
        assertThat(demo.hcType()).isEqualTo("ON");
        assertThat(demo.dob()).isEqualTo("19850615");
        assertThat(demo.dobYy()).isEqualTo("1985");
        assertThat(demo.dobMm()).isEqualTo("06");
        assertThat(demo.dobDd()).isEqualTo("15");
    }

    @Test
    void shouldExposeReferralDoctorAsRecord_mirroringFlatGetters() {
        BillingONFormViewModel m = BillingONFormViewModel.builder()
                .referralDoctor("Smith")
                .referralDoctorOhip("123456")
                .referralSpecialty("00")
                .build();

        BillingReferralDoctor r = m.getReferralDoctorRecord();
        assertThat(r.name()).isEqualTo("Smith");
        assertThat(r.ohip()).isEqualTo("123456");
        assertThat(r.specialty()).isEqualTo("00");
    }
}
