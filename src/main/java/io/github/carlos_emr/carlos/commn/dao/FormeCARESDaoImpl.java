/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.FormeCARES;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
public class FormeCARESDaoImpl extends AbstractDaoImpl<FormeCARES> implements FormeCARESDao {
    protected FormeCARESDaoImpl() {
        super(FormeCARES.class);
    }

    @Override
    public List<FormeCARES> findAllByFormCreatedDateDemographicNo(Date createDate, int demographicNo) {
        String sql = "select x from " + modelClass.getSimpleName() + " x where DATE(x.formCreated) = ?1 and x.demographicNo = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, createDate);
        query.setParameter(2, demographicNo);
        @SuppressWarnings("unchecked")
        List<FormeCARES> formeCARESList = query.getResultList();
        if (formeCARESList == null) {
            formeCARESList = Collections.emptyList();
        }
        return formeCARESList;
    }

    @Override
    public List<FormeCARES> findAllIncompleteByDemographicNumber(int demographicNo) {
        String sql = "select x from " + modelClass.getSimpleName() + " x where x.completed = ?1 and x.demographicNo = ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, false);
        query.setParameter(2, demographicNo);
        @SuppressWarnings("unchecked")
        List<FormeCARES> formeCARESList = query.getResultList();
        if (formeCARESList == null) {
            formeCARESList = Collections.emptyList();
        }
        return formeCARESList;
    }

}
