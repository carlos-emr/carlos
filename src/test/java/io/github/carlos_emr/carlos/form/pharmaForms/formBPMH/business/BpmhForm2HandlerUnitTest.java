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
