//CHECKSTYLE:OFF
/**
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
 */

package io.github.carlos_emr.carlos.PMmodule.web;

import java.util.TreeMap;

import io.github.carlos_emr.carlos.commn.model.IssueGroup;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.utility.AccumulatorMap;
import io.github.carlos_emr.carlos.utility.EncounterUtil;

/**
 * This bean is a essentially a huge data grid. The purpose of this
 * bean is to facilitate with generating population reports.
 */
public class PopulationReportDataObjects {

    /**
     * This map should hold the number of encounters for a given issueGroup.
     */
    public static class EncounterTypeDataRow extends AccumulatorMap<IssueGroup> {
        public int rowTotalUniqueEncounters = 0;
        public int rowTotalUniqueClients = 0;
    }

    /**
     * This object should hold a EncounterTypeDataRow for every encounter type in the system. (about 3 at the time of this writing).
     */
    public static class EncounterTypeDataGrid extends TreeMap<EncounterUtil.EncounterType, EncounterTypeDataRow> {
        public EncounterTypeDataRow subTotal = null;
    }

    /**
     * This object should hold a EncounterTypeDataGrid for every role in the system.
     */
    public static class RoleDataGrid extends TreeMap<SecRole, EncounterTypeDataGrid> {
        public EncounterTypeDataRow total = null;
    }

    /**
     * This object should hold a EncounterTypeDataGrid for every providers.
     */
    public static class ProviderDataGrid extends TreeMap<Provider, EncounterTypeDataGrid> {
        public EncounterTypeDataRow total = null;
    }

}
