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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationDto.Currentness;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalTransactions;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationDose;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes one prescription occurrence into the caller's fenced unit, without sending or prescribing. */
@Service
public class CdsMedicationPersister {
    private final DrugDao drugs;
    private final PartialDateDao dates;
    private final CdsClinicalNormalizationService normalization;

    public CdsMedicationPersister(DrugDao drugs, PartialDateDao dates,
            CdsClinicalNormalizationService normalization) {
        this.drugs = drugs;
        this.dates = dates;
        this.normalization = normalization;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsRecordDto record)
            throws IOException {
        return write(demographicNo, auditProviderNo, normalization.medication(record));
    }

    /** Normalize before entering the unit when the caller already has a validated candidate. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsMedicationDto source) {
        return write(demographicNo, auditProviderNo, source, null);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsMedicationDto source,
            CdsProviderMatchDto sourceProvider) {
        CdsClinicalTransactions.requireUnit();
        Drug drug = candidate(demographicNo, source, sourceProvider);
        // persist(), unlike legacy addNewDrug(), propagates failure to the transaction owner.
        drugs.persist(drug);
        drugs.flush();
        partial(drug.getId(), PartialDate.DRUGS_WRITTENDATE, source.written());
        partial(drug.getId(), PartialDate.DRUGS_STARTDATE, source.start());
        return Map.of("drugs", String.valueOf(drug.getId()));
    }

    Drug candidate(int demographicNo, CdsMedicationDto source) {
        return candidate(demographicNo, source, null);
    }

    Drug candidate(int demographicNo, CdsMedicationDto source, CdsProviderMatchDto sourceProvider) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid target patient");
        Drug drug = new Drug();
        drug.setDemographicId(demographicNo);
        // The importing actor is audited by the unit; it is not evidence of original authorship.
        drug.setImportedPrescriberNo(sourceProvider != null && sourceProvider.matched()
                ? sourceProvider.localProviderNo() : null);
        drug.setOutsideProviderName(source.prescriberName());
        drug.setOutsideProviderOhip(source.prescriberOhip());
        if (source.din() == null) drug.setCustomName(source.name());
        else drug.setBrandName(source.name());
        drug.setRegionalIdentifier(source.din());
        drug.setWrittenDate(source.written().dateStorageAnchor());
        drug.setRxDate(source.start().dateStorageAnchor());
        drug.setStartDateUnknown(!source.start().exactCalendarDate());
        drug.setEndDate(null); // Dispense duration is not treatment-plan discontinuation evidence.
        drug.setDuration(source.durationDays());
        drug.setDurUnit(source.durationDays() == null ? null : "D");
        drug.setQuantity(source.quantity());
        drug.setRepeat(source.repeats());
        if (source.refillDurationDays() != null) drug.setRefillDuration(source.refillDurationDays());
        if (source.refillQuantity() != null) drug.setRefillQuantity(source.refillQuantity());
        drug.setFreqCode(source.frequency());
        drug.setRoute(source.route());
        drug.setDrugForm(source.form());
        drug.setUnit(source.doseUnit());
        drug.setUnitName(source.doseUnit());
        drug.setCdsDoseText(source.dose());
        CdsMedicationDose.parse(source.dose()).ifPresent(dose -> {
            drug.setTakeMin(dose.minimum());
            drug.setTakeMax(dose.maximum());
        });
        drug.setDosage(strength(source));
        drug.setLongTerm(source.longTerm());
        drug.setPastMed(source.past());
        drug.setPatientCompliance(source.compliance());
        drug.setShortTerm(source.treatmentType() == null ? null
                : (source.treatmentType().equals("ACU") || source.treatmentType().equals("ONET")
                    || source.treatmentType().equals("PRN Short-term")));
        drug.setNoSubs(source.substitutionNotAllowed());
        drug.setNonAuthoritative(source.nonAuthoritative());
        drug.setETreatmentType(source.treatmentType());
        drug.setRxStatus(source.prescriptionStatus());
        drug.setCdsCurrentness(source.currentness().name());
        drug.setCdsProjectionIncomplete(source.sourceRequiresReconciliation());
        drug.setPrn(source.treatmentType() != null && source.treatmentType().startsWith("PRN ")
                || "PRN".equals(source.frequency()));
        drug.setArchived(source.currentness() == Currentness.HISTORICAL);
        drug.setArchivedReason(source.currentness() == Currentness.HISTORICAL
                ? "cds:" + (source.prescriptionStatus() == null ? "PastMedications" : source.prescriptionStatus()) : null);
        drug.setArchivedDate(null);
        drug.setDispenseInterval(source.dispenseInterval());
        drug.setProtocol(source.protocol());
        drug.setPriorRxProtocol(source.priorPrescription());
        drug.setImportedProviderNotes(source.providerNotes()); // Never the printed SIG.
        drug.setSpecial(instructions(source));
        drug.setSpecialInstruction(source.pharmacyInstructions());
        drug.setCustomInstructions(true);
        drug.setCustomNote(false);
        drug.setScriptNo(null); // A source prescription identifier is not a local executable script.
        drug.setPosition(0);
        drug.setCreateDate(new Date());
        return drug;
    }

    private void partial(int id, int field, CdsTemporalDto value) {
        if (value.legacyPartialFormat() != null) {
            dates.persist(new PartialDate(PartialDate.DRUGS, id, field, value.legacyPartialFormat()));
        }
    }

    private static String strength(CdsMedicationDto source) {
        if (source.strengthSourceOnly()) return "[Source strength: see imported medication details]";
        if (source.strengthAmount() == null) return null;
        return source.strengthAmount() + (source.strengthUnit() == null ? "" : " " + source.strengthUnit());
    }

    /** Preserves fractional/range dose literally instead of extracting a leading number. */
    private static String instructions(CdsMedicationDto source) {
        StringBuilder display = new StringBuilder();
        append(display, source.name() == null || source.name().isBlank() ? "Source DIN " + source.din() : source.name());
        append(display, strength(source));
        display.append('\n');
        append(display, source.dose());
        append(display, source.doseUnit());
        append(display, source.route());
        append(display, source.frequency());
        return display.toString();
    }

    private static void append(StringBuilder display, String part) {
        if (part != null && !part.isBlank()) {
            if (!display.isEmpty()) display.append(' ');
            display.append(part);
        }
    }
}
