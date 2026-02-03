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

package io.github.carlos_emr.carlos.report.data;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicQueryFavouritesDao;
import io.github.carlos_emr.carlos.commn.model.DemographicQueryFavourite;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author McMaster
 */
@SuppressWarnings("rawtypes")
public class RptSearchData {

    ArrayList rosterTypes;
    ArrayList patientTypes;
    ArrayList savedQueries;

    private DemographicQueryFavouritesDao demographicQueryFavouritesDao = SpringUtils.getBean(DemographicQueryFavouritesDao.class);

    /**
     * This function runs through the demographic table and retrieves all the roster types currently being used
     *
     * @return ArrayList  of roster status types in the demographic table
     */

    public ArrayList<String> getRosterTypes() {
        ArrayList<String> retval = new ArrayList<String>();
        DemographicDao dao = SpringUtils.getBean(DemographicDao.class);
        retval.addAll(dao.getAllRosterStatuses());
        return retval;
    }


    public ArrayList<String> getPatientTypes() {
        ArrayList<String> retval = new ArrayList<String>();
        DemographicDao dao = SpringUtils.getBean(DemographicDao.class);
        retval.addAll(dao.getAllPatientStatuses());
        return retval;
    }

    public ArrayList<String> getProvidersWithDemographics() {
        ArrayList<String> retval = new ArrayList<String>();
        DemographicDao dao = SpringUtils.getBean(DemographicDao.class);
        retval.addAll(dao.getAllProviderNumbers());
        return retval;
    }

    public ArrayList getQueryTypes() {
        ArrayList<SearchCriteria> retval = new ArrayList<SearchCriteria>();
        List<DemographicQueryFavourite> results = demographicQueryFavouritesDao.findByArchived("1");
        for (DemographicQueryFavourite result : results) {
            SearchCriteria sc = new SearchCriteria();
            sc.id = String.valueOf(result.getId());
            sc.queryName = result.getQueryName();

            retval.add(sc);
        }

        return retval;
    }

    public void deleteQueryFavourite(String id) {
        DemographicQueryFavourite d = demographicQueryFavouritesDao.find(Integer.parseInt(id));
        if (d != null) {
            d.setArchived("0");
            demographicQueryFavouritesDao.merge(d);
        }
    }

    public class SearchCriteria {
        public String id;
        public String queryName;
    }
}
