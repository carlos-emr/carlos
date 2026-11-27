/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.hl7.v2;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.hl7v2.model.v26.message.OMP_O09;
import ca.uhn.hl7v2.parser.PipeParser;
import io.github.carlos_emr.carlos.commn.model.Drug;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class OmpO09MedicationUnitTest {
    @Test
    void receiverCanReadDispenseAndDoseInTheirOwnFields() throws Exception {
        Drug drug = new Drug();
        drug.setRegionalIdentifier("01234567");
        drug.setUnit("tablet");
        drug.setDrugForm("Tablet");
        drug.setTakeMin(.5f);
        drug.setTakeMax(.5f);
        drug.setQuantity("7.50");
        drug.setUnitName("tablets");
        drug.setDosage("250.0 mg/5 mL + 31.25 mg/5 mL");
        OMP_O09 message = new OMP_O09();
        message.initQuickstart("OMP", "O09", "P");
        OmpO09Builder.fillRxo(message.getORDER().getRXO(), drug);
        String encoded = new PipeParser().encode(message);
        String[] fields = java.util.Arrays.stream(encoded.split("\r"))
                .filter(segment -> segment.startsWith("RXO|")).findFirst().orElseThrow().split("\\|", -1);
        assertThat(fields[1]).startsWith("01234567^");
        assertThat(fields[2]).isEqualTo("0.5");
        assertThat(fields[4]).contains("tablet");
        assertThat(fields[11]).isEqualTo("7.5");
        assertThat(fields[12]).contains("tablets");
        assertThat(fields[6]).contains("250.0 mg/5 mL + 31.25 mg/5 mL");
    }

    @Test
    void receiverReadsAmbiguousQuantityAsRecordedText() throws Exception {
        Drug drug = new Drug();
        drug.setDosage("120 mg/5 mL");
        drug.setUnit("mg");
        drug.setDrugForm("Suspension");
        drug.setQuantity("1/2 bottle");
        var segment = new OMP_O09().getORDER().getRXO();
        OmpO09Builder.fillRxo(segment, drug);
        assertThat(segment.getRequestedGiveAmountMinimum().getValue()).isNull();
        assertThat(segment.getRequestedDispenseAmount().getValue()).isNull();
        assertThat(segment.getProviderSPharmacyTreatmentInstructions(1).getText().getValue())
                .isEqualTo("Quantity as recorded: 1/2 bottle");
    }

    @Test
    void shouldIncludeSeparateUnitOnRecordedQuantityRangeDoseAndNormalizedTq1Duration() throws Exception {
        Drug rangeDrug = new Drug();
        rangeDrug.setUnit("tab");
        rangeDrug.setDrugForm("tab");
        rangeDrug.setTakeMin(0.5f);
        rangeDrug.setTakeMax(1.5f);
        rangeDrug.setQuantity("1/2");
        rangeDrug.setUnitName("bottle");
        rangeDrug.setDuration("7.0");
        rangeDrug.setDurUnit("D");
        rangeDrug.setFreqCode("BID");

        OMP_O09 message = new OMP_O09();
        message.initQuickstart("OMP", "O09", "P");
        OmpO09Builder.fillRxo(message.getORDER().getRXO(), rangeDrug);
        OmpO09Builder.fillTq1(message.getORDER(), rangeDrug);

        var rxo = message.getORDER().getRXO();
        assertThat(rxo.getRequestedGiveAmountMinimum().getValue()).isEqualTo("0.5");
        assertThat(rxo.getRequestedGiveAmountMaximum().getValue()).isEqualTo("1.5");
        assertThat(rxo.getProviderSPharmacyTreatmentInstructions(1).getText().getValue())
                .isEqualTo("Quantity as recorded: 1/2 bottle");
        assertThat(message.getORDER().getTIMING(0).getTQ1().getServiceDuration().getQuantity().getValue())
                .isEqualTo("7");
        assertThat(message.getORDER().getTIMING(0).getTQ1().getServiceDuration().getUnits().getText().getValue())
                .isEqualTo("D");

        Drug cdsDoseDrug = new Drug();
        cdsDoseDrug.setUnit("tablet");
        cdsDoseDrug.setDrugForm("Tablet");
        cdsDoseDrug.setCdsDoseText(" 2.50 ");
        var cdsRxo = new OMP_O09().getORDER().getRXO();
        OmpO09Builder.fillRxo(cdsRxo, cdsDoseDrug);
        assertThat(cdsRxo.getRequestedGiveAmountMinimum().getValue()).isEqualTo("2.5");
        assertThat(cdsRxo.getProviderSPharmacyTreatmentInstructions(1).getText().getValue())
                .isEqualTo("Dose as recorded:  2.50 ");
    }
}
