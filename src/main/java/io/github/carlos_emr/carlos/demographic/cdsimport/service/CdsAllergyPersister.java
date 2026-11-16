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

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalTransactions;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Faithful allergy/adverse-reaction rows; no guessed product or intolerance mapping. */
@Service
public class CdsAllergyPersister {
    private final AllergyDao allergies;
    private final PartialDateDao dates;
    private final CdsClinicalNormalizationService normalization;

    public CdsAllergyPersister(
            @org.springframework.beans.factory.annotation.Qualifier("allergyDao")
                    AllergyDao allergies,
            PartialDateDao dates,
            CdsClinicalNormalizationService normalization) {
        this.allergies = allergies;
        this.dates = dates;
        this.normalization = normalization;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsRecordDto record)
            throws IOException {
        return write(demographicNo, auditProviderNo, normalization.allergy(record));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(
            int demographicNo, String auditProviderNo, CdsAllergyDto source) {
        CdsClinicalTransactions.requireUnit();
        Allergy allergy = candidate(demographicNo, source);
        allergies.persist(allergy);
        allergies.flush();
        // Every row written is returned, so the unit's receipt names these rows and no other
        // date row that happens to carry the same allergy id.
        Map<String, String> written = new java.util.LinkedHashMap<>();
        written.put("allergies", String.valueOf(allergy.getId()));
        partial(allergy.getId(), PartialDate.ALLERGIES_STARTDATE, source.start(), "start", written);
        partial(
                allergy.getId(),
                PartialDate.ALLERGIES_ENTRYDATE,
                source.recorded(),
                "recorded",
                written);
        return written;
    }

    Allergy candidate(int demographicNo, CdsAllergyDto source) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid target patient");
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographicNo);
        allergy.setProviderNo(
                null); // No source-author field exists here; importing actor is separate audit.
        allergy.setDescription(source.description() == null ? source.code() : source.description());
        allergy.setReaction(source.reaction());
        allergy.setReactionType(source.reactionType());
        allergy.setNonDrug(
                "ND".equals(source.property())
                        ? Boolean.TRUE
                        : "DR".equals(source.property()) ? Boolean.FALSE : null);
        boolean atc = source.atcResidual();
        allergy.setTypeCode(atc ? 8 : 0); // DR alone does not establish catalogue identity.
        allergy.setRegionalIdentifier(atc ? null : source.code());
        allergy.setAtc(atc ? source.code() : null);
        allergy.setDrugrefId(null);
        allergy.setSeverityOfReaction(
                source.nativeSeverity() != null
                        ? source.nativeSeverity()
                        : switch (source.severity() == null ? "" : source.severity()) {
                    case "NO" -> "5";
                    case "MI" -> "1";
                    case "MO" -> "2";
                    case "LT" -> "3";
                    default -> null;
                });
        allergy.setLifeStage(source.lifeStage());
        allergy.setStartDate(source.start().dateStorageAnchor());
        allergy.setEntryDate(source.recorded().dateStorageAnchor());
        // A source archive marker is restored only when this import opted in (decision 9).
        allergy.setArchived(source.nativeArchived());
        if (source.ageOfOnset() != null) allergy.setAgeOfOnset(source.ageOfOnset());
        if (source.onsetOfReaction() != null) allergy.setOnsetOfReaction(source.onsetOfReaction());
        return allergy;
    }

    private void partial(
            int id, int field, CdsTemporalDto value, String name, Map<String, String> written) {
        if (value.legacyPartialFormat() != null) {
            PartialDate date =
                    new PartialDate(PartialDate.ALLERGIES, id, field, value.legacyPartialFormat());
            dates.persist(date);
            // The id the database gave the row; a row without one is never receipted.
            if (date.getId() == null || date.getId() < 1)
                throw new CdsImportException("ALLERGY_NATIVE_DATE_ID_MISSING");
            written.put("partial_date#" + name, date.getId().toString());
        }
    }
}
