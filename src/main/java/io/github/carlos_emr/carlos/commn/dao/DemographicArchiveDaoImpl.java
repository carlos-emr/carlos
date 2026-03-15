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

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicArchive;
import org.springframework.stereotype.Repository;

import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

@Repository
public class DemographicArchiveDaoImpl extends AbstractDaoImpl<DemographicArchive> implements DemographicArchiveDao {

    public DemographicArchiveDaoImpl() {
        super(DemographicArchive.class);
    }

    @Override
    public List<DemographicArchive> findByDemographicNo(Integer demographicNo) {

        String sqlCommand = "select x from DemographicArchive x where x.demographicNo=?1";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<DemographicArchive> results = query.getResultList();

        return (results);
    }

    @Override
    public List<DemographicArchive> findRosterStatusHistoryByDemographicNo(Integer demographicNo) {

        String sqlCommand = "select x from DemographicArchive x where x.demographicNo=?1 order by x.id desc";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<DemographicArchive> results = query.getResultList();

        // Remove entries with identical rosterStatus
        String this_rs, next_rs;
        Date this_rd, next_rd, this_td, next_td;
        for (int i = 0; i < results.size() - 1; i++) {
            this_rs = StringUtils.noNull(results.get(i).getRosterStatus());
            next_rs = StringUtils.noNull(results.get(i + 1).getRosterStatus());
            this_rd = results.get(i).getRosterDate();
            next_rd = results.get(i + 1).getRosterDate();
            this_td = results.get(i).getRosterTerminationDate();
            next_td = results.get(i + 1).getRosterTerminationDate();

            if (this_rs.equalsIgnoreCase(next_rs) &&
                    UtilDateUtilities.nullSafeCompare(this_rd, next_rd) == 0 &&
                    UtilDateUtilities.nullSafeCompare(this_td, next_td) == 0) {
                results.remove(i);
                i--;
            }
        }
        return (results);
    }

    @Override
    public Long archiveRecord(Demographic d) {
        DemographicArchive da = new DemographicArchive(d);
        persist(da);
        return da.getId();
    }

    @Override
    public List<DemographicArchive> findByDemographicNoChronologically(Integer demographicNo) {

        String sqlCommand = "select x from DemographicArchive x where x.demographicNo=?1 order by x.lastUpdateDate ASC";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<DemographicArchive> results = query.getResultList();

        return (results);
    }

    @Override
    public List<Object[]> findMetaByDemographicNo(Integer demographicNo) {

        String sqlCommand = "select x.id,x.demographicNo,x.lastUpdateUser,x.lastUpdateDate from DemographicArchive x where x.demographicNo=?1 order by x.lastUpdateDate DESC, x.id DESC";

        Query query = entityManager.createQuery(sqlCommand);
        query.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return (results);
    }
}
