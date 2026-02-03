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

package io.github.carlos_emr.carlos.managers;

import java.util.*;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

public interface PreventionManager {

    public List<Prevention> getUpdatedAfterDate(LoggedInInfo loggedInInfo, Date updatedAfterThisDateExclusive,
                                                int itemsToReturn);

    public List<Prevention> getByDemographicIdUpdatedAfterDate(LoggedInInfo loggedInInfo, Integer demographicId,
                                                               Date updatedAfterThisDateExclusive);

    public Prevention getPrevention(LoggedInInfo loggedInInfo, Integer id);

    public List<PreventionExt> getPreventionExtByPrevention(LoggedInInfo loggedInInfo, Integer preventionId);

    public ArrayList<String> getPreventionTypeList();

    public ArrayList<HashMap<String, String>> getPreventionTypeDescList();

    public boolean hideItem(String item);

    public void addCustomPreventionItems(String items);

    public void addPreventionWithExts(Prevention prevention, HashMap<String, String> exts);

    public List<Prevention> getPreventionsByProgramProviderDemographicDate(LoggedInInfo loggedInInfo, Integer programId,
                                                                           String providerNo, Integer demographicId, Calendar updatedAfterThisDateExclusive, int itemsToReturn);

    public List<Prevention> getPreventionsByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo);

    public String getWarnings(LoggedInInfo loggedInInfo, String demo);

    public String checkNames(String k);

    public boolean isDisabled();

    public boolean isCreated();

    public Set<String> getPreventionStopSigns();

    public boolean isPrevDisabled(String name);

    public List<String> getDisabledPreventions();

    public boolean isHidePrevItemExist();

    public boolean setDisabledPreventions(List<String> newDisabledPreventions);

    public List<Prevention> getImmunizationsByDemographic(LoggedInInfo loggedInInfo, Integer demographicNo);

    public String getCustomPreventionItems();

}
