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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Data logic of the ChartSpace Allergies block.
 *
 * <p>Kept out of the action so the action stays a gate. Auditing is already
 * done by {@link AllergyManager#getActiveAllergies}, so nothing is logged here
 * (and no patient identifier or allergy text would be).</p>
 */
// Extraction point: when a 2nd block exists, introduce ChartSpaceBlockLoader { blockId(); load(...) } and a registry.
@Service
public class AllergyBlockLoader {

    private final SecurityInfoManager securityInfoManager;
    private final AllergyManager allergyManager;

    /**
     * @param securityInfoManager privilege checker
     * @param allergyManager source of active allergies
     */
    @Autowired
    public AllergyBlockLoader(SecurityInfoManager securityInfoManager, AllergyManager allergyManager) {
        this.securityInfoManager = securityInfoManager;
        this.allergyManager = allergyManager;
    }

    /**
     * Loads the active allergies of a patient as a block response.
     *
     * <p>Contract: {@code NO_ACCESS} when {@code _allergy r} is missing for this
     * patient (the manager is not called), {@code EMPTY} when there are no active
     * allergies, {@code OK} with rows otherwise. "No access" is a state rather than
     * an exception so the page can show it distinctly from "no records".
     * Only {@link SecurityException} is converted (the manager re-checks the same
     * privilege); any other failure propagates so the client shows its error state.</p>
     *
     * @param info logged-in user
     * @param demographicNo patient id, already validated by the caller
     * @return block response, never null
     */
    public AllergyBlockDto load(LoggedInInfo info, int demographicNo) {
        if (!securityInfoManager.hasPrivilege(info, "_allergy", "r", String.valueOf(demographicNo))) {
            return new AllergyBlockDto(BlockStatus.NO_ACCESS, List.of());
        }

        List<Allergy> allergies;
        try {
            allergies = allergyManager.getActiveAllergies(info, demographicNo);
        } catch (SecurityException e) {
            return new AllergyBlockDto(BlockStatus.NO_ACCESS, List.of());
        }

        if (allergies == null || allergies.isEmpty()) {
            return new AllergyBlockDto(BlockStatus.EMPTY, List.of());
        }

        List<AllergyBlockDto.Item> items = allergies.stream()
                .map(a -> new AllergyBlockDto.Item(
                        a.getDescription(),
                        a.getSeverityOfReaction(),
                        a.getReaction(),
                        a.getStartDateFormatted()))
                .toList();
        return new AllergyBlockDto(BlockStatus.OK, items);
    }
}
