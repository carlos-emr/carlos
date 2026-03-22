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
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Handler that loads measurement types for a given group name, sorted by display name
 * and description. Also loads associated measuring instruction handlers for each type.
 * Used to populate CDM report configuration UIs with available measurement types.
 *
 * @see RptMeasurementTypesBean
 * @see RptMeasuringInstructionBeanHandler
 * @since 2001-01-01
 */
public class RptMeasurementTypesBeanHandler {

    Vector<RptMeasurementTypesBean> measurementTypeVector = new Vector<RptMeasurementTypesBean>();
    Vector<RptMeasuringInstructionBeanHandler> measuringInstrcBeanVector = new Vector<RptMeasuringInstructionBeanHandler>();

    public RptMeasurementTypesBeanHandler(String groupName) {
        init(groupName);
    }

    @SuppressWarnings("unchecked")
    public boolean init(String groupName) {
        boolean verdict = true;
        try {
            MeasurementGroupDao mgDao = SpringUtils.getBean(MeasurementGroupDao.class);
            MeasurementTypeDao mtDao = SpringUtils.getBean(MeasurementTypeDao.class);
            List<MeasurementGroup> groups = mgDao.findByName(groupName);
            Collections.sort(groups, Comparator.comparing(MeasurementGroup::getTypeDisplayName));
            for (MeasurementGroup g : groups) {
                String typeDisplayName = g.getTypeDisplayName();

                List<MeasurementType> mts = mtDao.findByTypeDisplayName(typeDisplayName);
                Collections.sort(mts, Comparator.comparing(MeasurementType::getTypeDescription));
                for (MeasurementType mt : mts) {
                    RptMeasurementTypesBean measurementTypes = new RptMeasurementTypesBean(mt.getId(), mt.getType(), mt.getTypeDisplayName(), mt.getTypeDescription(), mt.getMeasuringInstruction(), mt.getValidation());
                    measurementTypeVector.add(measurementTypes);

                    RptMeasuringInstructionBeanHandler hd = new RptMeasuringInstructionBeanHandler(typeDisplayName);
                    measuringInstrcBeanVector.add(hd);
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
