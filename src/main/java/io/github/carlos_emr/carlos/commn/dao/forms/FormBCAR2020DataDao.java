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
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Data;

import javax.persistence.Query;
import java.util.List;

public class FormBCAR2020DataDao extends AbstractDaoImpl<FormBCAR2020Data> {
    public FormBCAR2020DataDao() {
        super(FormBCAR2020Data.class);
    }

    public List<FormBCAR2020Data> findFields(Integer formId) {
        String sql = "select f from FormBCAR2020Data f " +
                "where f.formId = ?1";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter(1, formId);
        return query.getResultList();
    }

    public List<FormBCAR2020Data> findFieldsForPage(Integer formId, Integer pageNo) {
        String sql = "select f from FormBCAR2020Data f " +
                "where f.formId = ?1 and (f.pageNo = ?2 OR f.pageNo = 0)";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter(1, formId);
        query = query.setParameter(2, pageNo);
        return query.getResultList();
    }

    public FormBCAR2020Data findFieldForPage(Integer formId, Integer pageNo, String fieldName) {
        String sql = "SELECT f FROM FormBCAR2020Data f " +
                "WHERE f.formId = ?1 AND (f.pageNo = ?2 OR f.pageNo = 0) AND f.field = ?3";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter(1, formId);
        query = query.setParameter(2, pageNo);
        query = query.setParameter(3, fieldName);

        FormBCAR2020Data record = null;

        List<FormBCAR2020Data> results = query.getResultList();
        if (!results.isEmpty()) {
            record = results.get(0);
        }

        return record;
    }
}
