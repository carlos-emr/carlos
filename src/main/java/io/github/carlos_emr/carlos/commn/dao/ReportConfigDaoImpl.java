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

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ReportConfig;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link ReportConfigDao} for reporting data access.
 *
 * @since 2001
 */

public class ReportConfigDaoImpl extends AbstractDaoImpl<ReportConfig> implements ReportConfigDao {

    public ReportConfigDaoImpl() {
        super(ReportConfig.class);
    }

    /** {@inheritDoc} */

    @Override
    public List<ReportConfig> findByReportIdAndNameAndCaptionAndTableNameAndSave(int reportId, String name, String caption, String tableName, String save) {
        Query q = entityManager.createQuery("select x from ReportConfig x where x.reportId=?1 and x.name=?2 and x.caption=?3 and x.tableName=?4 and x.save=?5");
        q.setParameter(1, reportId);
        q.setParameter(2, name);
        q.setParameter(3, caption);
        q.setParameter(4, tableName);
        q.setParameter(5, save);

        @SuppressWarnings("unchecked")
        List<ReportConfig> results = q.getResultList();

        return results;
    }

    /** {@inheritDoc} */

    @Override
    public List<ReportConfig> findByReportIdAndSaveAndGtOrderNo(int reportId, String save, int orderNo) {
        Query q = entityManager.createQuery("select x from ReportConfig x where x.reportId=?1 and x.save=?2 and x.orderNo >= ?3 order by x.orderNo DESC");
        q.setParameter(1, reportId);
        q.setParameter(2, save);
        q.setParameter(3, orderNo);


        @SuppressWarnings("unchecked")
        List<ReportConfig> results = q.getResultList();

        return results;
    }
}
