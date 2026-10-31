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

    public CdsAllergyPersister(@org.springframework.beans.factory.annotation.Qualifier("allergyDao") AllergyDao allergies, PartialDateDao dates,
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
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsAllergyDto source) {
        CdsClinicalTransactions.requireUnit();
        Allergy allergy = candidate(demographicNo, source);
        allergies.persist(allergy);
        allergies.flush();
        partial(allergy.getId(), PartialDate.ALLERGIES_STARTDATE, source.start());
        partial(allergy.getId(), PartialDate.ALLERGIES_ENTRYDATE, source.recorded());
        return Map.of("allergies", String.valueOf(allergy.getId()));
    }

    Allergy candidate(int demographicNo, CdsAllergyDto source) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid target patient");
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(demographicNo);
        allergy.setProviderNo(null); // No source-author field exists here; importing actor is separate audit.
        allergy.setDescription(source.description() == null ? source.code() : source.description());
        allergy.setReaction(source.reaction());
        allergy.setReactionType(source.reactionType());
        allergy.setNonDrug("ND".equals(source.property()) ? Boolean.TRUE
                : "DR".equals(source.property()) ? Boolean.FALSE : null);
        allergy.setTypeCode(0); // DR does not establish brand/generic/class identity.
        allergy.setRegionalIdentifier(source.code());
        allergy.setDrugrefId(null);
        allergy.setSeverityOfReaction(switch (source.severity() == null ? "" : source.severity()) {
            case "NO" -> "5";
            case "MI" -> "1";
            case "MO" -> "2";
            case "LT" -> "3";
            default -> null;
        });
        allergy.setLifeStage(source.lifeStage());
        allergy.setStartDate(source.start().dateStorageAnchor());
        allergy.setEntryDate(source.recorded().dateStorageAnchor());
        allergy.setArchived(false);
        return allergy;
    }

    private void partial(int id, int field, CdsTemporalDto value) {
        if (value.legacyPartialFormat() != null) {
            dates.persist(new PartialDate(PartialDate.ALLERGIES, id, field, value.legacyPartialFormat()));
        }
    }
}
