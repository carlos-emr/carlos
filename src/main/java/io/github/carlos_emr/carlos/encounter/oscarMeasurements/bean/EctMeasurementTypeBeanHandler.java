//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * @author jay
 */
public class EctMeasurementTypeBeanHandler {
    private static Logger log = MiscUtils.getLogger();

    /**
     * Creates a new instance of EctMeasurementTypeBeanHandler
     */
    public EctMeasurementTypeBeanHandler() {
    }

    public EctMeasurementTypesBean getMeasurementType(String mType) {
        log.debug(" calling get MeasurementType");
        MeasurementTypes mt = MeasurementTypes.getInstance();
        return mt.getByType(mType);

    }

    public String getValidation(String val) {
        ValidationsDao dao = SpringUtils.getBean(ValidationsDao.class);
        Validations v = dao.find(ConversionUtils.fromIntString(val));
        if (v != null) {
            return v.getName();
        }
        return null;
    }
}
