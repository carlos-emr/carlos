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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * @deprecated use MeasurementTypeDao instead (2012-01-23)
 */
@Deprecated
public class MeasurementTypes {

    private static Logger log = MiscUtils.getLogger();

    private static MeasurementTypes measurementTypes;

    private Map<String, EctMeasurementTypesBean> byId = new HashMap<String, EctMeasurementTypesBean>();
    private Map<String, EctMeasurementTypesBean> byType = new HashMap<String, EctMeasurementTypesBean>();

    /**
     * Creates a new instance of MeasurementTypes
     */
    private MeasurementTypes() {
    }

    public EctMeasurementTypesBean getByType(String type) {
        return byType.get(type);
    }

    public EctMeasurementTypesBean getById(String type) {
        return byId.get(type);
    }

    public static synchronized MeasurementTypes getInstance() {
        if (measurementTypes == null) {
            measurementTypes = new MeasurementTypes();
            measurementTypes.reInit();
        }
        return measurementTypes;
    }

    public synchronized void reInit() {
        byId.clear();
        byType.clear();

        try {
            MeasurementTypeDao mtDao = SpringUtils.getBean(MeasurementTypeDao.class);
            ValidationsDao vDao = SpringUtils.getBean(ValidationsDao.class);

            for (MeasurementType t : mtDao.findAll()) {
                EctMeasurementTypesBean ret = new EctMeasurementTypesBean(t.getId(), t.getType(), t.getTypeDisplayName(), t.getTypeDescription(), t.getMeasuringInstruction(), t.getValidation());

                Validations v = vDao.find(ConversionUtils.fromIntString(t.getValidation()));
                if (v != null) {
                    ret.setValidationName(v.getName());
                }
                byId.put(t.getId().toString(), ret);
                byType.put(t.getType(), ret);

            }
        } catch (Exception e) {
            log.debug(e.getMessage());
        }
    }
}
