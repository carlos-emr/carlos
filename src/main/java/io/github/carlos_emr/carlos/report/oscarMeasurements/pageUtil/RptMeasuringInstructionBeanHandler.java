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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
 */
public class RptMeasuringInstructionBeanHandler {

    Vector<RptMeasuringInstructionBean> measuringInstrcVector = new Vector<RptMeasuringInstructionBean>();

    private final MeasurementTypeDao measurementTypeDao;
    private final MeasurementDao measurementDao;

    public RptMeasuringInstructionBeanHandler(String measurementType) {
        // Created per request by RptMeasurementTypesBeanHandler, not by Spring.
        this(measurementType, SpringUtils.getBean(MeasurementTypeDao.class), SpringUtils.getBean(MeasurementDao.class));
    }

    RptMeasuringInstructionBeanHandler(String measurementType, MeasurementTypeDao measurementTypeDao,
                                       MeasurementDao measurementDao) {
        this.measurementTypeDao = measurementTypeDao;
        this.measurementDao = measurementDao;
        init(measurementType);
    }

    /**
     * Loads the instructions for the measurement type with this display name.
     *
     * @param measurementType the measurement type's display name (as stored in {@code measurementGroup})
     * @return always {@code true}
     */
    public boolean init(String measurementType) {
        Set<String> instructions = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        for (MeasurementType mt : measurementTypeDao.findByTypeDisplayName(measurementType)) {
            instructions.add(mt.getMeasuringInstruction());
            types.add(mt.getType());
        }
        for (String type : types) {
            for (String stored : measurementDao.findDistinctMeasuringInstructionsByType(type)) {
                if (stored != null && !stored.isBlank()) {
                    instructions.add(stored);
                }
            }
        }
        for (String instruction : instructions) {
            measuringInstrcVector.add(new RptMeasuringInstructionBean(instruction));
        }
        return true;
    }

    public Vector<RptMeasuringInstructionBean> getMeasuringInstrcVector() {
        return measuringInstrcVector;
    }
}
