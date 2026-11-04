/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.model.MeasurementType;

/**
 * Lists the measuring instructions the CDM reports offer for one measurement type.
 *
 * <p>The reports select readings by exact {@code measurements.measuringInstruction}. A reading
 * keeps the instruction that was current when it was saved, so when a type's instruction changes
 * the older readings would silently drop out of every per-instruction line. For example, the
 * Asthma Action Plan (AACP) moved from {@code Yes/No} to {@code Provided/Revised/Reviewed} (issue
 * #3893). The list therefore holds the {@code measurementType} rows' current instructions first,
 * followed by any other instruction still stored on existing readings of the same type, so
 * legacy readings stay reportable next to the new ones.</p>
 *
 * <p>Stored instructions are user-entered text (the entry form's {@code inputMInstrc-*} field),
 * so a reading may carry any string, including patient-specific free text. This list is shown
 * to every {@code _report} reader for the whole clinic, so a stored instruction is offered only
 * when it is controlled: either an instruction some {@code measurementType} row of the same type
 * defines, or one of the retired instruction spellings in {@link #LEGACY_INSTRUCTIONS}. Free
 * text stays out of the list, and every page that renders the list must still encode it for its
 * context.</p>
 *
 * <p>Built by {@link RptMeasurementTypesBeanHandler}, which looks up the stored instructions of
 * every listed type in one query and passes the result in.</p>
 */
public class RptMeasuringInstructionBeanHandler {

    /**
     * Instruction spellings a {@code measurementType} row no longer carries but that readings
     * still hold: the Asthma Action Plan was seeded with {@code Yes/No} (rule {@code Yes/No/NA})
     * before migration V1.0.33 moved it to {@code Provided/Revised/Reviewed}. These are fixed
     * seed values, never free text, so they are safe to offer clinic-wide.
     */
    static final Set<String> LEGACY_INSTRUCTIONS = Set.of("Yes/No", "Yes/No/NA");

    Vector<RptMeasuringInstructionBean> measuringInstrcVector = new Vector<RptMeasuringInstructionBean>();

    /**
     * @param typesForDisplayName the {@code measurementType} rows sharing one display name
     * @param storedInstructionsByType the instructions stored on existing readings, keyed by type
     *                                 code, as returned by
     *                                 {@code MeasurementDao.findDistinctMeasuringInstructionsByTypes}
     */
    public RptMeasuringInstructionBeanHandler(List<MeasurementType> typesForDisplayName,
                                              Map<String, List<String>> storedInstructionsByType) {
        Set<String> instructions = new LinkedHashSet<>();
        for (MeasurementType mt : typesForDisplayName) {
            instructions.add(mt.getMeasuringInstruction());
        }
        Set<String> defined = new LinkedHashSet<>(instructions);
        for (MeasurementType mt : typesForDisplayName) {
            for (String stored : storedInstructionsByType.getOrDefault(mt.getType(), Collections.emptyList())) {
                if (isControlled(mt.getType(), stored, defined)) {
                    instructions.add(stored);
                }
            }
        }
        for (String instruction : instructions) {
            measuringInstrcVector.add(new RptMeasuringInstructionBean(instruction));
        }
    }

    /**
     * Whether a stored instruction may be offered clinic-wide: one a {@code measurementType} row
     * defines, or a retired seed spelling. Anything else is treated as free text and dropped.
     *
     * @param type the measurement type code; only AACP has these retired seed instructions
     * @param stored the {@code measurements.measuringInstruction} value; may be {@code null}
     * @param defined the instructions the type definitions carry
     */
    static boolean isControlled(String type, String stored, Set<String> defined) {
        if (stored == null || stored.isBlank()) {
            return false;
        }
        return defined.contains(stored) || ("AACP".equals(type) && LEGACY_INSTRUCTIONS.contains(stored));
    }

    public Vector<RptMeasuringInstructionBean> getMeasuringInstrcVector() {
        return measuringInstrcVector;
    }
}
