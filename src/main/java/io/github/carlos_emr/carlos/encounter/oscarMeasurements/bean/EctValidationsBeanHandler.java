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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
        List<Validations> vs = dao.findByDefinition(definitionFor(validation));
        if (vs.isEmpty()) {
            return -1;
        }
        return vs.get(0).getId();
    }

    public int addValidation(EctValidationsBean validation) {
        Validations definition = definitionFor(validation);
        dao.persist(definition);
        return definition.getId();
    }

    /** Lookup and creation agree on absent fields and the model's maxLength=0 default. */
    private static Validations definitionFor(EctValidationsBean validation) {
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
            v.setNumeric("1".equals(validation.isNumeric) || Boolean.parseBoolean(validation.isNumeric));
        }
        if (validation.isDate != null) {
            v.setDate("1".equals(validation.isDate) || Boolean.parseBoolean(validation.isDate));
        }

        return v;
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
