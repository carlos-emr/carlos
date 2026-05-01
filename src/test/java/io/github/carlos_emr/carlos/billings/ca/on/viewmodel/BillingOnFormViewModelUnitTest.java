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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingOnFormViewModel} builder round-trip + empty/defensive-copy semantics.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingOnFormViewModel")
@Tag("unit")
@Tag("billing")
class BillingOnFormViewModelUnitTest {

    @Test
    void shouldExposeBuilderFields_throughGetters() {
        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
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
        BillingOnFormViewModel model = BillingOnFormViewModel.builder().build();

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
        assertThat(model.getRequestParamEchoes()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyCollections_soMutatingTheBuilderInputDoesNotAffectTheModel() {
        java.util.ArrayList<String> dxList = new java.util.ArrayList<>();
        dxList.add("401");
        dxList.add("789");

        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
                .patientDx(dxList)
                .build();

        dxList.add("INJECTED-AFTER-BUILD");

        assertThat(model.getPatientDx()).containsExactly("401", "789");
    }

    @Test
    void shouldStoreProvidersRecord_asImmutableList() {
        List<BillingOnFormViewModel.ProviderOption> providers = List.of(
                new BillingOnFormViewModel.ProviderOption("Alpha", "Ada", "doc1|111111"),
                new BillingOnFormViewModel.ProviderOption("Beta", "Bob", "doc2|222222"));

        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
                .providers(providers)
                .build();

        assertThat(model.getProviders()).hasSize(2);
        assertThat(model.getProviders().get(0).lastName()).isEqualTo("Alpha");
        assertThat(model.getProviders().get(0).proOhip()).isEqualTo("doc1|111111");
        // Immutability is the test name's claim — assert it directly.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.getProviders().add(
                        new BillingOnFormViewModel.ProviderOption("Mut", "Mut", "x|y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldStoreBillingHistory_usingTheRecordShape() {
        List<BillingOnFormViewModel.BillingHistoryEntry> history = List.of(
                new BillingOnFormViewModel.BillingHistoryEntry(
                        "2026-04-20", "03", "0000", "401"));

        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
                .billingHistory(history)
                .build();

        assertThat(model.getBillingHistory()).hasSize(1);
        BillingOnFormViewModel.BillingHistoryEntry entry = model.getBillingHistory().get(0);
        assertThat(entry.visitDate()).isEqualTo("2026-04-20");
        assertThat(entry.visitType()).isEqualTo("03");
        assertThat(entry.clinicRefCode()).isEqualTo("0000");
        assertThat(entry.diagnosticCode()).isEqualTo("401");
    }

    @Test
    void shouldStoreServiceCodeGrid_asMapOfRecordLists() {
        BillingOnFormViewModel.ServiceCodeEntry code = new BillingOnFormViewModel.ServiceCodeEntry(
                "A001", "Consultation", "75.00", "100",
                "OFC", "Office", "", false);
        Map<String, List<BillingOnFormViewModel.ServiceCodeEntry>> grid = Map.of(
                "group1_OFC", List.of(code));

        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
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
        BillingOnFormViewModel model = BillingOnFormViewModel.builder()
                .billingForms(List.of(new BillingOnFormViewModel.BillingFormMenuEntry(
                        "OFC", "Office", "HCP")))
                .dxCodesByServiceType(Map.of("OFC",
                        List.of(new BillingOnFormViewModel.DxCodeEntry(
                                "OFC", "401", "Essential Hypertension"))))
                .billingFavourites(List.of("combo1", "Fav Combo"))
                .build();

        assertThat(model.getBillingForms()).hasSize(1);
        assertThat(model.getBillingForms().get(0).code()).isEqualTo("OFC");
        assertThat(model.getBillingForms().get(0).billType()).isEqualTo("HCP");
        assertThat(model.getDxCodesByServiceType()).containsKey("OFC");
        assertThat(model.getDxCodesByServiceType().get("OFC")).hasSize(1);
        assertThat(model.getBillingFavourites()).containsExactly("combo1", "Fav Combo");
        // Immutability is the test name's claim — assert it directly,
        // including the inner List<DxCodeEntry> inside the map (the model
        // uses copyOfNestedListMap to deep-freeze the value collections).
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.getBillingForms().add(
                        new BillingOnFormViewModel.BillingFormMenuEntry("X", "X", "X")))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.getDxCodesByServiceType().put("X", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.getDxCodesByServiceType().get("OFC").add(
                        new BillingOnFormViewModel.DxCodeEntry("X", "X", "X")))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> model.getBillingFavourites().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
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
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
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
     * Round-trip the demoDobInvalid flag (surfaces a banner on the form
     * when the patient's stored DOB is unparseable). Default must be false
     * so an unset model doesn't render a misleading banner.
     */
    @Test
    void shouldRoundTripDemoDobInvalid_throughBuilder() {
        BillingOnFormViewModel defaultModel = BillingOnFormViewModel.builder().build();
        assertThat(defaultModel.isDemoDobInvalid()).isFalse();

        BillingOnFormViewModel flagged = BillingOnFormViewModel.builder()
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
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
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
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
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

        // Assert against the flat getters, not literal strings — that's
        // the actual mirror property the JavaDoc claims to test. A future
        // refactor that drops a flat getter (or returns something different
        // from the record component) breaks loud here.
        BillingDemographicSummary demo = m.getDemographicSummary();
        assertThat(demo.firstName()).isEqualTo(m.getDemoFirst());
        assertThat(demo.lastName()).isEqualTo(m.getDemoLast());
        assertThat(demo.hin()).isEqualTo(m.getDemoHin());
        assertThat(demo.ver()).isEqualTo(m.getDemoVer());
        assertThat(demo.sex()).isEqualTo(m.getDemoSex());
        assertThat(demo.hcType()).isEqualTo(m.getDemoHcType());
        assertThat(demo.dob()).isEqualTo(m.getDemoDob());
        assertThat(demo.dobYy()).isEqualTo(m.getDemoDobYear());
        assertThat(demo.dobMm()).isEqualTo(m.getDemoDobMonth());
        assertThat(demo.dobDd()).isEqualTo(m.getDemoDobDay());
    }

    @Test
    void shouldExposeReferralDoctorAsRecord_mirroringFlatGetters() {
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
                .referralDoctor("Smith")
                .referralDoctorOhip("123456")
                .referralSpecialty("00")
                .build();

        BillingReferralDoctor r = m.getReferralDoctorRecord();
        assertThat(r.name()).isEqualTo(m.getReferralDoctor());
        assertThat(r.ohip()).isEqualTo(m.getReferralDoctorOhip());
        assertThat(r.specialty()).isEqualTo(m.getReferralSpecialty());
    }

    @Test
    @DisplayName("composed setter wins, flat getters delegate to the composed record")
    void shouldDelegateFlatGettersToComposedRecord_whenComposedSetterUsed() {
        // The composed-record setter is the preferred shape; when both are
        // supplied the composed wins and the flat getters delegate to it.
        BillingDemographicSummary demoComposed = new BillingDemographicSummary(
                "Composed", "Doe", "9999999999", "AB", "1", "ON",
                "19900101", "1990", "01", "01");
        BillingReferralDoctor refComposed = new BillingReferralDoctor(
                "Smith,Adam", "987654", "GP");
        BillingValidationMessages msgsComposed = new BillingValidationMessages(
                "1", "Composed error", "Composed warning");
        BillingMultisiteContext multiComposed = new BillingMultisiteContext(
                true, List.of(),
                "DefaultSite", "doc|111", "doc|111",
                Map.of("DefaultSite", "<option/>"),
                true, List.of(),
                "C0001");

        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
                // Flat setters: should be overridden by the composed record below.
                .demoFirst("Flat").demoLast("Wrong").demoHin("000")
                .referralDoctor("Wrong").referralDoctorOhip("000000")
                .errorFlag("").errorMsg("flat err").warningMsg("flat warn")
                .multisiteEnabled(false)
                // Composed setters take precedence.
                .demographic(demoComposed)
                .referral(refComposed)
                .messages(msgsComposed)
                .multisite(multiComposed)
                .build();

        // Composed record getters return the composed values, NOT the flats.
        assertThat(m.getDemographic()).isSameAs(demoComposed);
        assertThat(m.getReferral()).isSameAs(refComposed);
        assertThat(m.getMessages()).isSameAs(msgsComposed);
        assertThat(m.getMultisite()).isSameAs(multiComposed);

        // Flat getters delegate to the composed record (single source of truth).
        assertThat(m.getDemoFirst()).isEqualTo(demoComposed.firstName());
        assertThat(m.getDemoLast()).isEqualTo(demoComposed.lastName());
        assertThat(m.getDemoHin()).isEqualTo(demoComposed.hin());
        assertThat(m.getReferralDoctor()).isEqualTo(refComposed.name());
        assertThat(m.getReferralDoctorOhip()).isEqualTo(refComposed.ohip());
        assertThat(m.getReferralSpecialty()).isEqualTo(refComposed.specialty());
        assertThat(m.getErrorFlag()).isEqualTo(msgsComposed.errorFlag());
        assertThat(m.getErrorMsg()).isEqualTo(msgsComposed.errorMessage());
        assertThat(m.getWarningMsg()).isEqualTo(msgsComposed.warningMessage());
        assertThat(m.isMultisiteEnabled()).isTrue();
        assertThat(m.getDefaultSelectedSite()).isEqualTo("DefaultSite");
        assertThat(m.getSelectedClinicNbrPrefix()).isEqualTo("C0001");
    }

    @Test
    @DisplayName("composed accessors expose the same data as flat getters when only flats supplied")
    void shouldExposeComposed_fromFlatSetters() {
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
                .demoFirst("Jane").demoLast("Doe").demoHin("9876543225")
                .demoVer("AB").demoSex("F").demoHcType("ON")
                .demoDob("19850615").demoDobYear("1985").demoDobMonth("06").demoDobDay("15")
                .referralDoctor("Smith").referralDoctorOhip("123456").referralSpecialty("00")
                .errorFlag("1").errorMsg("err").warningMsg("warn")
                .multisiteEnabled(true).rmaEnabled(true).selectedClinicNbrPrefix("X")
                .build();

        // Composed records assembled from flat fields preserve every value.
        assertThat(m.getDemographic().firstName()).isEqualTo(m.getDemoFirst());
        assertThat(m.getDemographic().lastName()).isEqualTo(m.getDemoLast());
        assertThat(m.getReferral().name()).isEqualTo(m.getReferralDoctor());
        assertThat(m.getMessages().errorFlag()).isEqualTo(m.getErrorFlag());
        assertThat(m.getMessages().errorMessage()).isEqualTo(m.getErrorMsg());
        assertThat(m.getMessages().warningMessage()).isEqualTo(m.getWarningMsg());
        assertThat(m.getMultisite().enabled()).isEqualTo(m.isMultisiteEnabled());
        assertThat(m.getMultisite().rmaEnabled()).isEqualTo(m.isRmaEnabled());
        assertThat(m.getMultisite().selectedClinicNbrPrefix()).isEqualTo(m.getSelectedClinicNbrPrefix());
    }

    @Test
    @DisplayName("form view model exposes focused presentation slices")
    void shouldExposeFocusedPresentationSlices_fromFlatBuilderSetters() throws Exception {
        BillingOnFormViewModel m = BillingOnFormViewModel.builder()
                .userNo("doc1")
                .demographicNo("123")
                .appointmentNo("456")
                .providerNo("doc2")
                .apptProviderNo("doc3")
                .providerView("doc2|999999")
                .demoName("Doe,Jane")
                .today("2026-04-28")
                .billReferenceDate("2026-04-28")
                .mReview("Y")
                .ctlBillForm("GP")
                .curBillForm("PRI")
                .demoNameUrlEncoded("Doe%2CJane")
                .requestParamEchoes(Map.of("appointment_date", "2026-04-28"))
                .familyDoctor("Smith")
                .rosterStatus("RO")
                .assgProviderNo("doc4")
                .age(46)
                .patientDx(List.of("401"))
                .patientDxAddCode("ADD")
                .patientDxMatchCode("MATCH")
                .billingRecommendations("recommend")
                .billingHistoryRows(List.of(new BillingOnFormViewModel.BillingHistoryRow(
                        "1", "2026-04-01", "2026-04-01", "A001", "401", "2026-04-02")))
                .providers(List.of(new BillingOnFormViewModel.ProviderOption("Smith", "Ada", "doc2|999999")))
                .assgProviderDisplay("Smith, Ada")
                .clinicView("0000")
                .clinicNo("clinic")
                .visitType("03")
                .singleClickEnabled(true)
                .dxCode("401")
                .xmlVisitType("03")
                .xmlLocation("0000")
                .visitDate("2026-04-28")
                .defaultLocation("0000")
                .admissionDate("2026-04-20")
                .defaultXmlVdate("2026-04-20")
                .dxCodeDefault("401")
                .serviceDateDefault("2026-04-28")
                .billingFavourites(List.of("Fav", "A001"))
                .billingFavouriteOptions(List.of(new BillingOnFormViewModel.BillingFavouriteOption("Fav", "A001")))
                .facilityNumOptions(List.of(new BillingOnFormViewModel.FacilityNumOption("0000", "Clinic")))
                .legacySiteContextEnabled(true)
                .legacySiteOptions(List.of(new BillingOnFormViewModel.LegacySiteOption("site", true)))
                .displayMessage("msg")
                .primaryCareIncentive("PRI")
                .defaultView("GP")
                .build();

        Object requestContext = m.getClass().getMethod("getRequestContext").invoke(m);
        Object patient = m.getClass().getMethod("getPatient").invoke(m);
        Object providerPanel = m.getClass().getMethod("getProviderPanel").invoke(m);
        Object visit = m.getClass().getMethod("getVisit").invoke(m);
        Object lookupData = m.getClass().getMethod("getLookupData").invoke(m);
        Object display = m.getClass().getMethod("getDisplay").invoke(m);

        assertThat(requestContext.getClass().getMethod("demographicNo").invoke(requestContext))
                .isEqualTo(m.getDemographicNo());
        assertThat(patient.getClass().getMethod("patientDx").invoke(patient))
                .isEqualTo(m.getPatientDx());
        assertThat(providerPanel.getClass().getMethod("providerView").invoke(providerPanel))
                .isEqualTo(m.getProviderView());
        assertThat(visit.getClass().getMethod("dxCodeDefault").invoke(visit))
                .isEqualTo(m.getDxCodeDefault());
        assertThat(lookupData.getClass().getMethod("billingFavouriteOptions").invoke(lookupData))
                .isEqualTo(m.getBillingFavouriteOptions());
        assertThat(display.getClass().getMethod("displayMessage").invoke(display))
                .isEqualTo(m.getDisplayMessage());
    }

    @Test
    @DisplayName("billingON.jsp consumes presentation slices instead of flat form fields")
    void shouldUseComposedPresentationSlicesForMigratedBillingOnJspFields() throws Exception {
        String jsp = Files.readString(Path.of(
                "src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingON.jsp"));

        assertThat(jsp).contains("formModel.requestContext.requestParamEchoes");
        assertThat(jsp).contains("formModel.providerPanel.providers");
        assertThat(jsp).contains("formModel.visit.dxCodeDefault");
        assertThat(jsp).contains("formModel.lookupData.billingFavouriteOptions");
        assertThat(jsp).contains("formModel.display.displayMessage");

        assertThat(jsp).doesNotContain("formModel.requestParamEchoes");
        assertThat(jsp).doesNotContain("formModel.providers");
        assertThat(jsp).doesNotContain("formModel.dxCodeDefault");
        assertThat(jsp).doesNotContain("formModel.billingFavouriteOptions");
        assertThat(jsp).doesNotContain("formModel.displayMessage");
    }
}
