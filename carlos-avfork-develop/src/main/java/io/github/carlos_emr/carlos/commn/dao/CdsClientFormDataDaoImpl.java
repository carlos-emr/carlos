/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CdsClientFormData;
import org.springframework.stereotype.Repository;

@Repository
public class CdsClientFormDataDaoImpl extends AbstractDaoImpl<CdsClientFormData> implements CdsClientFormDataDao {

    public CdsClientFormDataDaoImpl() {
        super(CdsClientFormData.class);
    }

    public List<CdsClientFormData> findByQuestion(Integer cdsClientFormId, String question) {

        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.cdsClientFormId=?1 and x.question=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, cdsClientFormId);
        query.setParameter(2, question);

        @SuppressWarnings("unchecked")
        List<CdsClientFormData> results = query.getResultList();

        return (results);
    }

    /**
     * Generally speaking this method is good for getting a form if
     * the answer is a CDS category, i.e. "019-04", this method is not
     * useful for answers which are random strings like "days hosipitalised"
     * where the answer may conflict with other numeric answers.
     */
    public CdsClientFormData findByAnswer(Integer cdsClientFormId, String answer) {

        String sqlCommand = "select x from " + modelClass.getSimpleName() + " x where x.cdsClientFormId=?1 and x.answer=?2";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, cdsClientFormId);
        query.setParameter(2, answer);

        return (getSingleResultOrNull(query));
    }

}
