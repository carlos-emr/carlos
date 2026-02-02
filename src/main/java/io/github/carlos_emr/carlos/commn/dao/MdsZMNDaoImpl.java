//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.MdsZMN;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unchecked")
public class MdsZMNDaoImpl extends AbstractDaoImpl<MdsZMN> implements MdsZMNDao {

    public MdsZMNDaoImpl() {
        super(MdsZMN.class);
    }

    @Override
    public MdsZMN findBySegmentIdAndReportName(Integer id, String reportName) {
        Query query = createQuery("z", "z.id = ?1 AND z.reportName = ?2");
        query.setParameter(1, id);
        query.setParameter(2, reportName);
        return getSingleResultOrNull(query);
    }

    @Override
    public MdsZMN findBySegmentIdAndResultMnemonic(Integer id, String rm) {
        Query query = createQuery("z", "z.id = ?1 and z.resultMnemonic = ?2");
        query.setParameter(1, id);
        query.setParameter(2, rm);
        return getSingleResultOrNull(query);
    }

    @Override
    public List<String> findResultCodes(Integer id, String reportSequence) {
        String sql = "SELECT zmn.resultCode FROM MdsZMN zmn WHERE zmn.id = ?1 " +
                "AND zmn.reportGroup = ?2 ";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        query.setParameter(2, reportSequence);
        List<Object[]> resultCodes = query.getResultList();
        List<String> result = new ArrayList<String>(resultCodes.size());
        for (Object[] o : resultCodes) {
            result.add(String.valueOf(o[0]));
        }
        return result;
    }
}
