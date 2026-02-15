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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.provider.web;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * @author rjonasz
 */
public class ProviderPropertyAction {

    /**
     * typically set from inside the JSP class providerupdatepreference.jsp
     *
     * @param request
     */
    public static void updateOrCreateProviderProperties(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        UserPropertyDAO propertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String propertyValue;
        UserProperty property;

        propertyValue = StringUtils.trimToNull(request.getParameter(UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS));
        property = propertyDAO.getProp(providerNo, UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
        }
        property.setValue(String.valueOf(Boolean.parseBoolean(propertyValue)));
        propertyDAO.saveProp(property);
    }
}
