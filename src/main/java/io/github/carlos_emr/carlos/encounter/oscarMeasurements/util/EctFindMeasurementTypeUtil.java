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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.io.InputStream;
import java.util.Vector;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.prop.EctFormProp;

//import com.ibatis.commons.resources.Resources;
/*
 * Author: Ivy Chan
 * Company: iConcept Technologes Inc.
 * Created on: October 31, 2004
 */

/**
 * Utility class for looking up measurement type definitions by type code or name.
 *
 * @since 2001-01-01
 */
public class EctFindMeasurementTypeUtil {

    private static MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);

    public EctFindMeasurementTypeUtil() {
    }

    static public EctFormProp getEctMeasurementsType(InputStream is) {
        EctFormProp ret = null;
        try {
            JAXBContext ctx = JAXBContext.newInstance(EctFormProp.class);
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            ret = (EctFormProp) unmarshaller.unmarshal(is);
        } catch (Exception exc) {
            MiscUtils.getLogger().error("Error", exc);
        }
        return ret;
    }


    /**
     * Compare the form definition xml file with the measurementtype table in the database.
     * If a measurment type found in the definition file but not in the database, add a new type to the measurementtype table
     */
    static public Vector checkMeasurmentTypes(InputStream is, String formName) {

        EctFormProp formProp = getEctMeasurementsType(is);

        Vector measurementTypes = EctFormProp.getMeasurementTypes();

        for (int i = 0; i < measurementTypes.size(); i++) {
            EctMeasurementTypesBean mt = (EctMeasurementTypesBean) measurementTypes.elementAt(i);

            if (!measurementTypeIsFound(mt, formName)) {
                addMeasurementType(mt, formName);
            }
        }
        return measurementTypes;
    }

    static public boolean measurementTypeIsFound(EctMeasurementTypesBean mt, String formName) {
        boolean verdict = true;
        if (dao.findByTypeAndMeasuringInstruction(mt.getType(), mt.getMeasuringInstrc()).size() == 0) {
            verdict = false;
        }

        return verdict;
    }

    static public boolean measurementTypeKeyIsFound(EctMeasurementTypesBean mt) {
        boolean verdict = true;
        if (dao.findByType(mt.getType()).size() == 0) {
            verdict = false;
        }

        return verdict;
    }


    static public void addMeasurementType(EctMeasurementTypesBean mt, String formName) {

        //Find validation if not found add validation
        Vector validations = mt.getValidationRules();
        if (validations.size() > 0) {
            EctValidationsBean validation = (EctValidationsBean) validations.elementAt(0);
            EctValidationsBeanHandler vHd = new EctValidationsBeanHandler();
            int validationId = vHd.findValidation(validation);
            if (validationId < 0) {
                validationId = vHd.addValidation(validation);
            }
            MeasurementType m = new MeasurementType();
            m.setType(mt.getType());
            m.setTypeDisplayName(mt.getTypeDisplayName());
            m.setTypeDescription(mt.getTypeDesc());
            m.setMeasuringInstruction(mt.getMeasuringInstrc());
            m.setValidation(String.valueOf(validationId));
            dao.persist(m);

        }

    }

}
