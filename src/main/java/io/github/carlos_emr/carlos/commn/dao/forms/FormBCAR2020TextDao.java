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
import io.github.carlos_emr.carlos.form.model.FormBCAR2020Text;

import jakarta.persistence.Query;
import java.util.List;

public class FormBCAR2020TextDao extends AbstractDaoImpl<FormBCAR2020Text> {
    public FormBCAR2020TextDao() {
        super(FormBCAR2020Text.class);
    }

    public List<FormBCAR2020Text> findFields(Integer formId) {
        String sql = "select f from FormBCAR2020Text f " +
                "where f.formId = :formId";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter("formId", formId);
        return query.getResultList();
    }

    public List<FormBCAR2020Text> findFieldsForPage(Integer formId, Integer pageNo) {
        String sql = "select f from FormBCAR2020Text f " +
                "where f.formId = :formId and (f.pageNo = :pageNo OR f.pageNo = 0)";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter("formId", formId);
        query = query.setParameter("pageNo", pageNo);
        return query.getResultList();
    }

    public FormBCAR2020Text findFieldForPage(Integer formId, Integer pageNo, String fieldName) {
        String sql = "SELECT f FROM FormBCAR2020Text f " +
                "WHERE f.formId = :formId AND (f.pageNo = :pageNo OR f.pageNo = 0) AND f.field = :fieldName";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter("formId", formId);
        query = query.setParameter("pageNo", pageNo);
        query = query.setParameter("fieldName", fieldName);

        FormBCAR2020Text record = null;

        List<FormBCAR2020Text> results = query.getResultList();
        if (!results.isEmpty()) {
            record = results.get(0);
        }

        return record;
    }
}
