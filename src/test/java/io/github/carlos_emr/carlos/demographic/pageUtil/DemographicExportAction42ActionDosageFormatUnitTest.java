/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The legacy CDS 4 export wrote {@code Dosage} with {@code Float.toString}, so a dose of 1 left as
 * "1.0" and 500 as "500.0". ISMP Canada's <i>Do Not Use</i> list bans trailing zeros ("1.0 mg" read
 * as 10 mg) and missing leading zeros (".5 mg" read as 5 mg) (#6185).
 */
@Tag("unit")
@DisplayName("Legacy CDS export dose text")
class DemographicExportAction42ActionDosageFormatUnitTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "1.0, 1",
        "2.0, 2",
        "500.0, 500",
        "0.5, 0.5",
        "0.25, 0.25",
        "1.5, 1.5",
        "0.1, 0.1",
        "10000000.0, 10000000",
        "0.0, 0"
    })
    @DisplayName("should write a dose with no trailing zero, no exponent and a leading zero")
    void shouldWritePlainDecimal_forDose(float dose, String expected) {
        assertThat(DemographicExportAction42Action.plainDosage(dose)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should never produce the do-not-use forms")
    void shouldAvoidDoNotUseForms_forCommonDoses() {
        for (float dose : new float[] {0.5f, 1f, 2f, 2.5f, 5f, 10f, 12.5f, 250f, 1000f}) {
            String text = DemographicExportAction42Action.plainDosage(dose);
            assertThat(text).doesNotEndWith(".0").doesNotStartWith(".").doesNotContain("E");
        }
    }

    @Test
    @DisplayName("should treat a missing dose as zero")
    void shouldReturnZero_forNullDose() {
        assertThat(DemographicExportAction42Action.plainDosage(null)).isEqualTo("0");
    }

    @Test
    void shouldWriteDoseAndUnitsToTheLegacyCdsMedicationRecord() throws Exception {
        var prescription = new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription(0, "1", 7);
        prescription.setUnit("tablet");
        prescription.setDrugForm("Tablet");
        prescription.setDosage("250 mg");
        prescription.setTakeMin(.5f);
        prescription.setTakeMax(.5f);
        var record = cds.MedicationsAndTreatmentsDocument.Factory.newInstance();
        DemographicExportAction42Action.writeMedicationDose(record.addNewMedicationsAndTreatments(), prescription);
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var xml = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(record.xmlText())));
        assertThat(xml.getElementsByTagNameNS("*", "Dosage").item(0).getTextContent()).isEqualTo("0.5");
        assertThat(xml.getElementsByTagNameNS("*", "DosageUnitOfMeasure").item(0).getTextContent()).isEqualTo("tablet");
    }
}
