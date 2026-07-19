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


package io.github.carlos_emr.carlos.commn.dao.forms;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.form.model.BooleanValueForm;
import io.github.carlos_emr.carlos.form.model.FormBooleanValue;

import javax.persistence.Query;
import java.util.HashMap;

@Repository
public class FormBooleanValueDao extends AbstractDaoImpl<FormBooleanValue> {

    public FormBooleanValueDao() {
        super(FormBooleanValue.class);
    }

    public HashMap<String, FormBooleanValue> findAllForForm(BooleanValueForm form) {
        String sql = "SELECT value FROM FormBooleanValue value WHERE value.id.formName = ?1 AND value.id.formId = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, form.getFormTable());
        query.setParameter(2, form.getId());

        HashMap<String, FormBooleanValue> results = new HashMap<String, FormBooleanValue>();
        for (Object o : query.getResultList()) {
            FormBooleanValue booleanValue = (FormBooleanValue) o;
            results.put(booleanValue.getId().getFieldName(), booleanValue);
        }

        return results;
    }
}
