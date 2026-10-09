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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

public class RptMeasurementTypesBeanHandler {

    Vector<RptMeasurementTypesBean> measurementTypeVector = new Vector<RptMeasurementTypesBean>();
    Vector<RptMeasuringInstructionBeanHandler> measuringInstrcBeanVector = new Vector<RptMeasuringInstructionBeanHandler>();

    public RptMeasurementTypesBeanHandler(String groupName) {
        init(groupName);
    }

    /**
     * Loads the group's measurement types and, for each, the instructions a CDM report offers.
     *
     * <p>The instructions stored on existing readings are read for all of the group's types in
     * a single query, not one query per type, because the lookup scans {@code measurements}.</p>
     */
    public boolean init(String groupName) {
        boolean verdict = true;
        try {
            MeasurementGroupDao mgDao = SpringUtils.getBean(MeasurementGroupDao.class);
            MeasurementTypeDao mtDao = SpringUtils.getBean(MeasurementTypeDao.class);
            MeasurementDao measurementDao = SpringUtils.getBean(MeasurementDao.class);
            List<MeasurementGroup> groups = mgDao.findByName(groupName);
            Collections.sort(groups, Comparator.comparing(MeasurementGroup::getTypeDisplayName));

            List<List<MeasurementType>> typesByGroup = new ArrayList<>();
            Set<String> typeCodes = new LinkedHashSet<>();
            for (MeasurementGroup g : groups) {
                List<MeasurementType> mts = new ArrayList<>(mtDao.findByTypeDisplayName(g.getTypeDisplayName()));
                Collections.sort(mts, Comparator.comparing(MeasurementType::getTypeDescription));
                typesByGroup.add(mts);
                for (MeasurementType mt : mts) {
                    typeCodes.add(mt.getType());
                }
            }
            Map<String, List<String>> storedInstructions =
                    measurementDao.findDistinctMeasuringInstructionsByTypes(typeCodes);

            for (List<MeasurementType> mts : typesByGroup) {
                for (MeasurementType mt : mts) {
                    RptMeasurementTypesBean measurementTypes = new RptMeasurementTypesBean(mt.getId(), mt.getType(), mt.getTypeDisplayName(), mt.getTypeDescription(), mt.getMeasuringInstruction(), mt.getValidation());
                    measurementTypeVector.add(measurementTypes);

                    // Row N of the report forms reads the Nth handler, so one per type row.
                    measuringInstrcBeanVector.add(new RptMeasuringInstructionBeanHandler(mts, storedInstructions));
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            verdict = false;
        }
        return verdict;
    }

    public Vector<RptMeasurementTypesBean> getMeasurementTypeVector() {
        return measurementTypeVector;
    }

    public Vector<RptMeasuringInstructionBeanHandler> getMeasuringInstrcBeanVector() {
        return measuringInstrcBeanVector;
    }
}
