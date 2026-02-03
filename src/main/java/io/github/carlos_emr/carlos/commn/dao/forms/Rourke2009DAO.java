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

import java.util.List;

import javax.persistence.Query;

import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;

import io.github.carlos_emr.carlos.form.model.FormRourke2009;

/**
 * @author rjonasz
 */
@Repository
public class Rourke2009DAO extends AbstractDaoImpl<FormRourke2009> {

    public Rourke2009DAO() {
        super(FormRourke2009.class);
    }

    @SuppressWarnings("unchecked")
    public List<FormRourke2009> findAllDistinctForms(Integer demographicNo) {
        String sql = "select frm from FormRourke2009 frm where frm.demographicNo = ?1 and frm.id = (select max(frm2.id) from FormRourke2009 frm2 where frm2.formCreated = frm.formCreated and frm2.demographicNo = frm.demographicNo)";
        Query query = entityManager.createQuery(sql);
        query = query.setParameter(1, demographicNo);
        return query.getResultList();
    }

}
