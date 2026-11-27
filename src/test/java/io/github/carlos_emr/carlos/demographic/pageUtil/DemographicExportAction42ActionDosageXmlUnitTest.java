/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import cds.OmdCdsDocument;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugReasonDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.utility.LegacyXmlBeans;

/** Exercises the dosage written by the legacy CDS 4.2 medication export loop. */
@Tag("unit")
@Tag("demographic")
class DemographicExportAction42ActionDosageXmlUnitTest extends DemographicExportExecuteUnitTestBase {

    @Test
    void shouldWriteDosageWithoutATrailingZero_forAWholeNumberDose() throws Exception {
        int patientId = 101;
        chart(patientId, "FAKE-Ann");
        Drug drug = new Drug();
        drug.setId(31);
        drug.setBrandName("FAKE Example");
        drug.setDosage("5 mg");
        drug.setUnit("tablet");
        drug.setTakeMin(1.0f);
        drug.setComment("FAKE medication note");

        DrugDao drugs = createAndRegisterMock(DrugDao.class);
        when(drugs.findByDemographicIdOrderByPositionForExport(patientId, null)).thenReturn(List.of(drug));
        registerMock(DrugReasonDao.class, mock(DrugReasonDao.class));
        when(request.getParameter("exReportsReceived")).thenReturn(null);
        when(request.getParameter("exMedicationsAndTreatments")).thenReturn("on");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        Map<String, byte[]> archive = streamedArchive();
        String patientFile = patientXml(patientId, "FAKE-Ann");
        assertThat(archive).containsKey(patientFile);
        OmdCdsDocument document = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                new String(archive.get(patientFile), StandardCharsets.UTF_8));
        assertThat(document.getOmdCds().getPatientRecord().getMedicationsAndTreatmentsArray())
                .singleElement().satisfies(medication -> {
                    assertThat(medication.getDosage()).isEqualTo("1");
                    assertThat(medication.getDosageUnitOfMeasure()).isEqualTo("tablet");
                });
    }
}
