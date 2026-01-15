//CHECKSTYLE:OFF
/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2025.
 */
package ca.openosp.openo.encounter.data;

import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.ProviderDefaultProgramDao;
import ca.openosp.openo.util.LabelValueBean;

import java.util.Date;
import java.util.List;

public interface EctProgramManager {
    List<LabelValueBean> getProgramBeans();

    List<LabelValueBean> getProgramBeans(String providerNo, Integer facilityId);

    List<LabelValueBean> getProgramBeansByFacilityId(Integer facilityId);

    List<LabelValueBean> getProgramForApptViewBeans(String providerNo, Integer facilityId);

    List<LabelValueBean> getDemographicByBedProgramIdBeans(int programId, Date dt, String archiveView);

    int getDefaultProgramId();

    int getDefaultProgramId(String providerNo);

    void setDefaultProgramId(String providerNo, int programId);

    Boolean getProviderSig(String providerNo);

    void toggleSig(String providerNo);

    ProgramProviderDAO getProgramProviderDAOT();

    String[] getProgramInformation(int programId);
}
