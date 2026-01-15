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
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.ProviderDefaultProgramDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.ProviderDefaultProgram;
import org.springframework.transaction.annotation.Transactional;
import ca.openosp.openo.util.LabelValueBean;

import java.util.*;

@Transactional
public class EctProgramManagerImpl implements EctProgramManager {
    private final DemographicDao demographicDao;
    private final ProgramProviderDAO programProviderDAOT;
    private final ProviderDefaultProgramDao providerDefaultProgramDao;
    private final ProgramDao programDao;

    /**
     * Constructor-based dependency injection.
     *
     * @param demographicDao the demographic DAO
     * @param programProviderDAO the program provider DAO
     * @param providerDefaultProgramDao the provider default program DAO
     * @param programDao the program DAO
     */
    public EctProgramManagerImpl(
            DemographicDao demographicDao,
            ProgramProviderDAO programProviderDAO,
            ProviderDefaultProgramDao providerDefaultProgramDao,
            ProgramDao programDao) {
        this.demographicDao = demographicDao;
        this.programProviderDAOT = programProviderDAO;
        this.providerDefaultProgramDao = providerDefaultProgramDao;
        this.programDao = programDao;
    }

    public List<LabelValueBean> getProgramBeans() {
        Iterator<Program> iter = programDao.getAllPrograms().iterator();
        ArrayList<LabelValueBean> pList = new ArrayList<LabelValueBean>();
        while (iter.hasNext()) {
            Program p = iter.next();
            if (p != null) {
                pList.add(new LabelValueBean(p.getName(), p.getId().toString()));
            }
        }
        return pList;
    }

    public List<LabelValueBean> getProgramBeans(String providerNo, Integer facilityId) {
        if (providerNo == null || "".equalsIgnoreCase(providerNo.trim())) return new ArrayList<LabelValueBean>();
        Iterator<ProgramProvider> iter = programProviderDAOT.getProgramProvidersByProvider(providerNo).iterator();
        ArrayList<LabelValueBean> pList = new ArrayList<LabelValueBean>();
        while (iter.hasNext()) {
            ProgramProvider p = iter.next();
            if (p != null && p.getProgramId() != null && p.getProgramId().longValue() > 0) {
                Program program = programDao.getProgram(Integer.valueOf(p.getProgramId().intValue()));

                // Check if program is null before accessing its methods
                if (program == null) continue;

                if (facilityId != null && program.getFacilityId() != facilityId.intValue()) continue;

                if (program.isActive())
                    pList.add(new LabelValueBean(program.getName(), program.getId().toString()));
            }
        }
        return pList;
    }

    public List<LabelValueBean> getProgramBeansByFacilityId(Integer facilityId) {
        if (facilityId <= 0) return new ArrayList<LabelValueBean>();
        Iterator<Program> iter = providerDefaultProgramDao.findProgramsByFacilityId(facilityId).iterator();
        ArrayList<LabelValueBean> pList = new ArrayList<LabelValueBean>();
        while (iter.hasNext()) {
            Program p = iter.next();
            if (p != null && p.getId() != null && p.getName() != null && p.getName().length() > 0) {
                pList.add(new LabelValueBean(p.getName(), p.getId().toString()));
            }
        }
        return pList;
    }

    public List<LabelValueBean> getProgramForApptViewBeans(String providerNo, Integer facilityId) {
        if (providerNo == null || "".equalsIgnoreCase(providerNo.trim())) return new ArrayList<LabelValueBean>();
        Iterator<ProgramProvider> iter = programProviderDAOT.getProgramProvidersByProvider(providerNo).iterator();
        ArrayList<LabelValueBean> pList = new ArrayList<LabelValueBean>();
        while (iter.hasNext()) {
            ProgramProvider p = iter.next();
            if (p != null && p.getProgramId() != null && p.getProgramId().longValue() > 0) {
                Program program = programDao.getProgramForApptView(Integer.valueOf(p.getProgramId().intValue()));
                if (program == null) continue;
                if (facilityId != null && program.getFacilityId() != facilityId.intValue()) continue;

                if (program.isActive()) pList.add(new LabelValueBean(program.getName(), program.getId().toString()));
            }
        }
        return pList;
    }

    public List<LabelValueBean> getDemographicByBedProgramIdBeans(int programId, Date dt, String archiveView) {
        /*default time is Oscar default null time 0001-01-01.*/
        Date defdt = new GregorianCalendar(1, 0, 1).getTime();

        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        dt = cal.getTime();
        Iterator<Demographic> iter;

        if (archiveView != null && archiveView.equals("true"))
            iter = demographicDao.getArchiveDemographicByProgramOptimized(programId, dt, defdt).iterator();
        else iter = demographicDao.getActiveDemographicByProgram(programId, dt, defdt).iterator();

        ArrayList<LabelValueBean> demographicList = new ArrayList<LabelValueBean>();
        Demographic de = null;
        while (iter.hasNext()) {
            de = iter.next();
            demographicList.add(new LabelValueBean(de.getLastName() + ", " + de.getFirstName(), de.getDemographicNo().toString()));

        }
        return demographicList;
    }

    public int getDefaultProgramId() {
        String defProgramName = "Annex";
        Program program = programDao.getProgramByName(defProgramName);
        if (program == null) return 1;
        else return program.getId();
    }

    public int getDefaultProgramId(String providerNo) {
        int defProgramId = 0;
        List<ProviderDefaultProgram> rs = providerDefaultProgramDao.getProgramByProviderNo(providerNo);
        if (rs.isEmpty()) {
            return defProgramId;
        } else return rs.get(0).getProgramId();

    }

    public void setDefaultProgramId(String providerNo, int programId) {
        providerDefaultProgramDao.setDefaultProgram(providerNo, programId);
    }

    public Boolean getProviderSig(String providerNo) {
        List<ProviderDefaultProgram> list = providerDefaultProgramDao.getProgramByProviderNo(providerNo);
        if (list.isEmpty()) {
            ProviderDefaultProgram pdp = new ProviderDefaultProgram();
            pdp.setProgramId(Integer.valueOf(0));
            pdp.setProviderNo(providerNo);
            pdp.setSign(false);
            providerDefaultProgramDao.saveProviderDefaultProgram(pdp);
            return Boolean.FALSE;
        }
        ProviderDefaultProgram pro = list.get(0);
        return Boolean.valueOf(pro.isSign());

    }

    public void toggleSig(String providerNo) {
        providerDefaultProgramDao.toggleSig(providerNo);

    }

    public ProgramProviderDAO getProgramProviderDAOT() {
        return programProviderDAOT;
    }

    public String[] getProgramInformation(int programId) {
        Program program = programDao.getProgram(programId);
        if (program == null) return new String[0];
        return new String[] { program.getName(), String.valueOf(program.getId()) };
    }
}
