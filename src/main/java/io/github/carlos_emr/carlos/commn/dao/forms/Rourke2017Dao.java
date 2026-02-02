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


package io.github.carlos_emr.carlos.commn.dao.forms;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.form.model.FormBooleanValue;
import io.github.carlos_emr.carlos.form.model.FormRourke2017;

import javax.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


@Repository
public class Rourke2017Dao extends AbstractDaoImpl<FormRourke2017> {

    public Rourke2017Dao() {
        super(FormRourke2017.class);
    }

    @SuppressWarnings("unchecked")
    public List<FormRourke2017> findAllDistinctForms(Integer demographicNo) {
        String sql = "select frm from FormRourke2017 frm where frm.demographicNo = ?1 and frm.id = (select max(frm2.id) from FormRourke2017 frm2 where frm2.formCreated = frm.formCreated and frm2.demographicNo = frm.demographicNo)";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter(1, demographicNo);
        return query.getResultList();
    }

    @Override
    public FormRourke2017 find(Object id) {
        // get form
        FormRourke2017 form = super.find(id);

        // set boolean fields from FormBooleanValues
        FormBooleanValueDao formBooleanValueDao = SpringUtils.getBean(FormBooleanValueDao.class);
        HashMap<String, FormBooleanValue> booleanValueMap = formBooleanValueDao.findAllForForm(form);
        form.setBooleanValueMap(booleanValueMap);

        return form;
    }


    public FormRourke2017 saveEntity(FormRourke2017 form) {
        // save FormRourke2017 object
        super.saveEntity(form);

        // get boolean fields and persist them:
        for (FormBooleanValue value : form.getBooleanValueMap().values()) {
            value.getId().setFormId(form.getId());
            value.getId().setFormName(FormRourke2017.FORM_TABLE);
        }
        FormBooleanValueDao formBooleanValueDao = SpringUtils.getBean(FormBooleanValueDao.class);
        formBooleanValueDao.batchPersist(new ArrayList(form.getBooleanValueMap().values()));
        return form;
    }
}
