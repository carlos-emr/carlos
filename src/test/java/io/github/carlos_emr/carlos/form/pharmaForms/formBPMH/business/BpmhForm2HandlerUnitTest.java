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
package io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.business;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugReasonDao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.dao.Icd9SynonymDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.commn.model.Icd9Synonym;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhDrug;
import io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.bean.BpmhForm2Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A new BPMH form posts no drug rows, so its bean's drug list is null; merging the patient's current
 * medications into it threw {@code NullPointerException} (#4731).
 */
@DisplayName("BpmhForm2Handler Tests")
@Tag("unit")
@Tag("form")
class BpmhForm2HandlerUnitTest {

    @Test
    void shouldListCurrentMedications_forNewFormWithoutDrugRows() {
        Drug drug = new Drug();
        drug.setId(55);
        drug.setCustomName("FAKE atorvastatin");
        drug.setSpecial("FAKE atorvastatin 20 mg\nTake 1 tablet daily");
        DrugDao drugDao = mock(DrugDao.class);
        when(drugDao.findByDemographicId(1001, false)).thenReturn(List.of(drug));
        DrugReasonDao drugReasonDao = mock(DrugReasonDao.class);
        when(drugReasonDao.getReasonsForDrugID(anyInt(), eq(true))).thenReturn(List.of());

        BpmhForm2Handler handler = new BpmhForm2Handler();
        handler.setBpmhFormBean(new BpmhForm2Bean());
        handler.setDrugDao(drugDao);
        handler.setDrugReasonDao(drugReasonDao);
        handler.setDemographicNo(1001);

        handler.setFormBeanDrugList(null);

        List<BpmhDrug> drugs = handler.getBpmhFormBean().getDrugs();
        assertThat(drugs).hasSize(1);
        assertThat(drugs.get(0).getId()).isEqualTo("55");
        assertThat(drugs.get(0).getHow()).isEqualTo("Take 1 tablet daily");
    }

    /**
     * "How I take it" falls back to the structured dose when the stored instruction text has no
     * direction line. Spring's {@code BeanUtils.copyProperties} skips a {@code float} to
     * {@code String} copy, so the dose never reached the form and the line read "Take TAB BID"
     * with no amount (#6185).
     */
    @ParameterizedTest(name = "take {0}-{1} -> \"{2}\"")
    @CsvSource(delimiter = '|', value = {
        "1 | 1 | Take 1 TAB BID PO",
        "0.5 | 0.5 | Take 0.5 TAB BID PO",
        "0.0625 | 0.0625 | Take 0.0625 TAB BID PO",
        "500 | 500 | Take 500 TAB BID PO",
        "1 | 2 | Take 1 to 2 TAB BID PO",
        "1 | 3 | Take 1 to 3 TAB BID PO",
        "2 | 0 | Take 2 TAB BID PO",
        "0.5 | 1 | Take 0.5 to 1 TAB BID PO"
    })
    void shouldWriteStructuredDose_whenInstructionTextHasNoDirectionLine(float takeMin, float takeMax,
            String expected) {
        Drug drug = structuredDrug(takeMin, takeMax);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getHow().trim()).isEqualTo(expected);
    }

    @Test
    void shouldLeaveDoseOut_whenNoStructuredDoseIsRecorded() {
        // 0 is the column default for "no structured dose", not a dose of zero tablets.
        Drug drug = structuredDrug(0f, 0f);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getHow().trim()).isEqualTo("");
    }

    @ParameterizedTest(name = "take {0}-{1}")
    @CsvSource({"-1, -1", "-1, 0", "0, -1"})
    void shouldLeaveDoseOut_whenTheLegacyEditorStoredItsUnspecifiedMarker(float takeMin, float takeMax) {
        // The legacy editor stores -1 for a dose it was not given; it is not a dose either.
        Drug drug = structuredDrug(takeMin, takeMax);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getHow().trim()).isEqualTo("");
    }

    @Test
    void shouldKeepAsNeededInStructuredDirections_whenThePrescriptionIsPrn() {
        Drug drug = structuredDrug(1f, 2f);
        drug.setPrn(true);
        assertThat(formRowFor(drug).getHow()).isEqualTo("Take 1 to 2 TAB BID PO as needed");
        drug.setSpecial("Take only as directed by the clinic");
        assertThat(formRowFor(drug).getHow()).isEqualTo("Take only as directed by the clinic");
    }

    @ParameterizedTest
    @CsvSource({"0, 2", "-1, 3"})
    void shouldLeaveDirectionsMissing_whenOnlyTheUpperBoundIsRecorded(float min, float max) {
        Drug drug = structuredDrug(min, max);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getTakeMin()).isEmpty();
        assertThat(row.getHow()).isEmpty();
    }

    @Test
    void shouldUseRecordedDoseUnit_whenProductFormDiffers() {
        Drug drug = structuredDrug(5, 5);
        drug.setUnit("mg");
        assertThat(formRowFor(drug).getHow()).isEqualTo("Take 5 mg BID PO");
    }

    @Test
    void shouldLeaveDirectionsMissing_whenOnlyProductStrengthAndFormAreKnown() {
        Drug drug = structuredDrug(5, 5);
        drug.setBrandName(drug.getCustomName());
        drug.setCustomName(null);
        drug.setDosage("25 mg");
        drug.setUnit("mg");
        assertThat(formRowFor(drug).getHow()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"3,1", "NaN,1", "1,Infinity"})
    void shouldLeaveDirectionsMissing_whenBoundsAreInvalid(float min, float max) {
        assertThat(formRowFor(structuredDrug(min, max)).getHow()).isEmpty();
    }

    @Test
    void shouldKeepTaperSteps_whenRecordAlsoContainsPharmacyAndDispenseLines() {
        Drug drug = structuredDrug(5, 5);
        drug.setSpecialInstruction("Supply blister pack");
        drug.setSpecial("FAKE metoprolol\r\nTake 1 tablet for 2 days\r\nThen take half a tablet"
                + "\r\nSupply blister pack\r\nQty:30.0 TAB Repeats:0");
        assertThat(formRowFor(drug).getHow()).isEqualTo("Take 1 tablet for 2 days\nThen take half a tablet");
    }

    @Test
    void shouldKeepAdministrationDirections_whenLegacyEditorWritesRepeatsOnTheirOwnLine() {
        Drug drug = structuredDrug(5, 5);
        drug.setSpecialInstruction("Supply blister pack");
        drug.setSpecial("FAKE metoprolol\r\nUse once daily\r\nSupply blister pack\r\nQty:30 \r\nRepeats:2 No Subs\r\n");

        assertThat(formRowFor(drug).getHow()).isEqualTo("Use once daily");
    }

    @Test
    void shouldPreserveExtraTextOnQtyLine_whenLegacyEditorWritesRepeatsOnTheirOwnLine() {
        Drug drug = structuredDrug(1, 1);
        drug.setSpecial("Take one tablet daily\nQty:7 tablets. Take with food.\nRepeats:0");

        assertThat(formRowFor(drug).getHow())
                .isEqualTo("Take one tablet daily\nQty:7 tablets. Take with food.\nRepeats:0");
    }

    @Test
    void shouldUseTheRecordedCdsUnit_whenProductFormAndStrengthDiffer() {
        Drug drug = structuredDrug(5, 5);
        drug.setDosage("10 mg");
        drug.setUnit("mL");
        drug.setCdsDoseText("5 mL");
        assertThat(formRowFor(drug).getHow()).isEqualTo("Take 5 mL BID PO");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "0.1, mL, Take 0.1 mL BID PO",
            "1/2, tablet, Take 1/2 tablet BID PO",
            "5 mL, mL, Take 5 mL BID PO",
            "5 mL, L, Take 5 mL L BID PO"
    })
    void shouldFallbackToCdsDoseText_whenFloatDoseIsAbsent(String cdsDose, String unit, String expectedHow) {
        Drug drug = structuredDrug(0, 0);
        drug.setDosage("10 mg");
        drug.setUnit(unit);
        drug.setCdsDoseText(cdsDose);
        assertThat(formRowFor(drug).getHow()).isEqualTo(expectedHow);
    }

    @Test
    void shouldUseDrugUnitOnlyWhenMatchingDrugForm_forCommercialProductWithoutCdsDose() {
        Drug matching = structuredDrug(1, 1);
        matching.setCustomName(null);
        matching.setBrandName("FAKE metoprolol 25 mg");
        matching.setDrugForm("TAB");
        matching.setUnit("TAB");
        assertThat(formRowFor(matching).getHow()).isEqualTo("Take 1 TAB BID PO");

        Drug strengthUnit = structuredDrug(1, 1);
        strengthUnit.setCustomName(null);
        strengthUnit.setBrandName("FAKE metoprolol 25 mg");
        strengthUnit.setDrugForm("TAB");
        strengthUnit.setUnit("mg");
        assertThat(formRowFor(strengthUnit).getHow()).isEmpty();
    }

    @Test
    void shouldStripPharmacyInstructions_forMultiwordAndFractionalDispenseFooters() {
        Drug multiword = structuredDrug(1, 1);
        multiword.setQuantity("2");
        multiword.setUnitName("prefilled syringes");
        multiword.setSpecialInstruction("Supply blister pack");
        multiword.setSpecial("Take once daily\nSupply blister pack\nQty:2 prefilled syringes Repeats:0");
        assertThat(formRowFor(multiword).getHow()).isEqualTo("Take once daily");

        Drug fractional = structuredDrug(1, 1);
        fractional.setQuantity("1/2");
        fractional.setUnitName("bottle");
        fractional.setSpecialInstruction("Supply blister pack");
        fractional.setSpecial("Take once daily\nSupply blister pack\nQty:1/2 bottle Repeats:0");
        assertThat(formRowFor(fractional).getHow()).isEqualTo("Take once daily");

        Drug authoredOnQtyLine = structuredDrug(1, 1);
        authoredOnQtyLine.setQuantity("2");
        authoredOnQtyLine.setUnitName("prefilled syringes");
        authoredOnQtyLine.setSpecialInstruction("Supply blister pack");
        authoredOnQtyLine.setSpecial("Take once daily\nQty:2 prefilled syringes. Keep refrigerated. Repeats:0");
        assertThat(formRowFor(authoredOnQtyLine).getHow())
                .isEqualTo("Take once daily\nQty:2 prefilled syringes. Keep refrigerated. Repeats:0");
    }

    @Test
    void shouldBuildAnEmptyMedicationList_whenTheDaoReturnsNull() {
        DrugDao drugDao = mock(DrugDao.class);
        when(drugDao.findByDemographicId(1001, false)).thenReturn(null);
        BpmhForm2Handler handler = new BpmhForm2Handler();
        handler.setBpmhFormBean(new BpmhForm2Bean());
        handler.setDrugDao(drugDao);
        handler.setDemographicNo(1001);
        handler.setFormBeanDrugList(null);
        assertThat(handler.getBpmhFormBean().getDrugs()).isEmpty();
    }

    @Test
    void shouldKeepReasonsIndependent_whenCodesAreMixedMissingOrUnmapped() {
        DrugReasonDao reasons = mock(DrugReasonDao.class);
        Icd9SynonymDao translations = mock(Icd9SynonymDao.class);
        Icd9Dao dictionary = mock(Icd9Dao.class);
        BpmhForm2Handler handler = new BpmhForm2Handler();
        handler.setDrugReasonDao(reasons);
        handler.setIcd9Dao(dictionary);
        handler.setDxCodeTranslationsDao(translations);
        Icd9Synonym translation = new Icd9Synonym();
        translation.setPatientFriendly("First indication");
        when(translations.findPatientFriendlyTranslationFor("001")).thenReturn(translation);
        Icd9 entry = new Icd9();
        entry.setDescription("Dictionary description");
        when(dictionary.findByCode("002")).thenReturn(entry);
        when(translations.findPatientFriendlyTranslationFor("003")).thenReturn(new Icd9Synonym());
        when(dictionary.findByCode("003")).thenReturn(new Icd9());
        when(reasons.getReasonsForDrugID(56, true)).thenReturn(List.of(
                reason("icd9", "001", "recorded detail"), reason("other", "001", "second detail"),
                reason(null, null, " author's wording "), reason("ICD9", "002", null),
                reason("ICD9", "unmapped", null), reason(null, "original", null), reason(null, null, null),
                reason("ICD9", "003", null), reason("\u0131CD9", "001", null)));

        assertThat(handler.getDrugReason(56)).isEqualTo("First indication recorded detail\nother 001 second detail"
                + "\n author's wording \nDictionary description\nICD9 unmapped\noriginal\nICD9 003\n\u0131CD9 001");
        when(reasons.getReasonsForDrugID(78, true)).thenReturn(null);
        assertThat(handler.getDrugReason(78)).isEmpty();
    }

    private static DrugReason reason(String system, String code, String comment) {
        DrugReason reason = new DrugReason();
        reason.setCodingSystem(system);
        reason.setCode(code);
        reason.setComments(comment);
        return reason;
    }

    private static Drug structuredDrug(float takeMin, float takeMax) {
        Drug drug = new Drug();
        drug.setId(56);
        drug.setCustomName("FAKE metoprolol");
        // One line only: no direction line for "How I take it" to read.
        drug.setSpecial("FAKE metoprolol 25 mg");
        drug.setMethod("Take");
        drug.setTakeMin(takeMin);
        drug.setTakeMax(takeMax);
        drug.setDrugForm("TAB");
        drug.setUnit("TAB");
        drug.setFreqCode("BID");
        drug.setRoute("PO");
        return drug;
    }

    private static BpmhDrug formRowFor(Drug drug) {
        DrugDao drugDao = mock(DrugDao.class);
        when(drugDao.findByDemographicId(1001, false)).thenReturn(List.of(drug));
        DrugReasonDao drugReasonDao = mock(DrugReasonDao.class);
        when(drugReasonDao.getReasonsForDrugID(anyInt(), eq(true))).thenReturn(List.of());

        BpmhForm2Handler handler = new BpmhForm2Handler();
        handler.setBpmhFormBean(new BpmhForm2Bean());
        handler.setDrugDao(drugDao);
        handler.setDrugReasonDao(drugReasonDao);
        handler.setDemographicNo(1001);

        handler.setFormBeanDrugList(null);

        List<BpmhDrug> drugs = handler.getBpmhFormBean().getDrugs();
        assertThat(drugs).hasSize(1);
        return drugs.get(0);
    }

    @Test
    void shouldTakeOnlyInstructionFromPostedRows_forCurrentMedications() {
        Drug drug = new Drug();
        drug.setId(55);
        drug.setCustomName("FAKE atorvastatin");
        drug.setSpecial("FAKE atorvastatin 20 mg\nTake 1 tablet daily");
        DrugDao drugDao = mock(DrugDao.class);
        when(drugDao.findByDemographicId(1001, false)).thenReturn(List.of(drug));
        DrugReasonDao drugReasonDao = mock(DrugReasonDao.class);
        when(drugReasonDao.getReasonsForDrugID(anyInt(), eq(true))).thenReturn(List.of());
        BpmhDrug postedMatch = new BpmhDrug();
        postedMatch.setId("55");
        postedMatch.setInstruction("continue at discharge");
        postedMatch.setWhat("a drug the patient does not take");
        postedMatch.setHow("posted directions");
        BpmhDrug postedUnknown = new BpmhDrug();
        postedUnknown.setId("99");
        postedUnknown.setWhat("not a current medication");
        BpmhForm2Bean posted = new BpmhForm2Bean();
        posted.setDrugs(new java.util.ArrayList<>(List.of(postedMatch, postedUnknown)));

        BpmhForm2Handler handler = new BpmhForm2Handler();
        handler.setBpmhFormBean(posted);
        handler.setDrugDao(drugDao);
        handler.setDrugReasonDao(drugReasonDao);
        handler.setDemographicNo(1001);

        handler.setFormBeanDrugList(null);

        List<BpmhDrug> drugs = handler.getBpmhFormBean().getDrugs();
        assertThat(drugs).extracting(BpmhDrug::getId).containsExactly("55");
        assertThat(drugs.get(0).getInstruction()).isEqualTo("continue at discharge");
        assertThat(drugs.get(0).getWhat()).doesNotContain("does not take");
        assertThat(drugs.get(0).getHow()).isEqualTo("Take 1 tablet daily");
    }
}
