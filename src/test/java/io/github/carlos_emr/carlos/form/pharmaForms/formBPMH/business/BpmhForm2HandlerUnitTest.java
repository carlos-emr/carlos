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
import io.github.carlos_emr.carlos.commn.model.Drug;
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

        assertThat(row.getHow().trim()).isEqualTo("Take TAB BID PO");
    }

    @ParameterizedTest(name = "take {0}-{1}")
    @CsvSource({"-1, -1", "-1, 0", "0, -1"})
    void shouldLeaveDoseOut_whenTheLegacyEditorStoredItsUnspecifiedMarker(float takeMin, float takeMax) {
        // The legacy editor stores -1 for a dose it was not given; it is not a dose either.
        Drug drug = structuredDrug(takeMin, takeMax);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getHow().trim()).isEqualTo("Take TAB BID PO");
    }

    @Test
    void shouldWriteUpperBoundAlone_whenOnlyTheUpperBoundIsRecorded() {
        Drug drug = structuredDrug(0f, 2f);

        BpmhDrug row = formRowFor(drug);

        assertThat(row.getHow().trim()).isEqualTo("Take 2 TAB BID PO");
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
