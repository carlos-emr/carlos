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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class EctValidationsBeanHandler {

    Vector validationsVector = new Vector();
    private ValidationsDao dao = SpringUtils.getBean(ValidationsDao.class);

    public EctValidationsBeanHandler() {
        init();
    }

    public boolean init() {

        boolean verdict = true;

        List<Validations> vs = dao.findAll();
        Collections.sort(vs, Validations.NameComparator);
        for (Validations v : vs) {
            EctValidationsBean validation = new EctValidationsBean(v.getName(), v.getId());
            validationsVector.add(validation);
        }

        return verdict;
    }

    public Collection getValidationsVector() {
        return validationsVector;
    }

    public int findValidation(EctValidationsBean validation) {
        String regularExp = validation.getRegularExp() != null ? validation.getRegularExp() : null;
        Double minValue = validation.getMinValue() != null ? ConversionUtils.fromDoubleString(validation.getMinValue()) : null;
        Double maxValue = validation.getMaxValue() != null ? ConversionUtils.fromDoubleString(validation.getMaxValue()) : null;
        Integer minLength = validation.getMinLength() != null ? ConversionUtils.fromIntString(validation.getMinLength()) : null;
        Integer maxLength = validation.getMaxLength() != null ? ConversionUtils.fromIntString(validation.getMaxLength()) : null;
        Boolean isNumeric = validation.getIsNumeric() != null ? ConversionUtils.fromBoolString(validation.getIsNumeric()) : null;
        Boolean isDate = validation.getIsDate() != null ? ConversionUtils.fromBoolString(validation.getIsDate()) : null;

        ValidationsDao dao = SpringUtils.getBean(ValidationsDao.class);
        List<Validations> vs = dao.findByAll(regularExp, minValue, maxValue, minLength, maxLength, isNumeric, isDate);
        if (vs.isEmpty()) {
            return -1;
        }
        return vs.get(0).getId();
    }

    public int addValidation(EctValidationsBean validation) {
        int validationId = -1;

        Validations v = new Validations();
        if (validation.getName() != null && !validation.getName().isEmpty()) {
            v.setName(validation.getName());
        }
        if (validation.getRegularExp() != null && !validation.getRegularExp().isEmpty()) {
            v.setRegularExp(validation.getRegularExp());
        }
        if (validation.getMinValue() != null && !validation.getMinValue().isEmpty()) {
            v.setMinValue(Double.parseDouble(validation.getMinValue()));
        }
        if (validation.getMaxValue() != null && !validation.getMaxValue().isEmpty()) {
            v.setMaxValue(Double.parseDouble(validation.getMaxValue()));
        }
        if (validation.getMinLength() != null && !validation.getMinLength().isEmpty()) {
            v.setMinLength(Integer.parseInt(validation.getMinLength()));
        }
        if (validation.getMaxLength() != null && !validation.getMaxLength().isEmpty()) {
            v.setMaxLength(Integer.parseInt(validation.getMaxLength()));
        }
        if (validation.isNumeric != null) {
            v.setNumeric(validation.isNumeric.equals("1") ? true : false);
        }
        if (validation.isDate != null) {
            v.setDate(validation.isDate.equals("1") ? true : false);
        }

        dao.persist(v);

        validationId = v.getId();

        return validationId;
    }

    public EctValidationsBean getValidation(String val) {
        EctValidationsBean validation;
        ValidationsDao dao = SpringUtils.getBean(ValidationsDao.class);
        List<Validations> vs = dao.findByName(val);
        if (vs.isEmpty()) {
            validation = new EctValidationsBean();
        } else {
            validation = new EctValidationsBean(vs.get(0));
        }
        return validation;

    }
}
