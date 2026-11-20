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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

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
        // A single-column select returns the column values themselves, not Object[] rows.
        String sql = "SELECT zmn.resultCode FROM MdsZMN zmn WHERE zmn.id = ?1 " +
                "AND zmn.reportGroup = ?2 AND zmn.resultCode IS NOT NULL";
        TypedQuery<String> query = entityManager.createQuery(sql, String.class);
        query.setParameter(1, id);
        query.setParameter(2, reportSequence);
        return new ArrayList<>(query.getResultList());
    }

    @Override
    public List<Object[]> findMnemonicsBySegmentId(Integer id) {
        // Scalar columns, not entities: every mnemonic row of a report shares the segmentID mapped as
        // @Id, so an entity query would hand back the first row once per row.
        String sql = "SELECT zmn.resultMnemonic, zmn.reportName, zmn.units, zmn.referenceRange " +
                "FROM MdsZMN zmn WHERE zmn.id = ?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, id);
        return query.getResultList();
    }
}
