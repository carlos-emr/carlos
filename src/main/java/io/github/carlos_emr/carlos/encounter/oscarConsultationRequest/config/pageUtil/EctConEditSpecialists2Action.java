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


package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil;

import java.io.IOException;
import java.util.ResourceBundle;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

public class EctConEditSpecialists2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws ServletException, IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_con", "u", null)) {
            throw new SecurityException("missing required sec object (_con)");
        }

        ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);

        String specId = this.getSpecId();
        String delete = this.getDelete();
        String specialists[] = this.getSpecialists();

        ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());

        if (delete.equals(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.EditSpecialists.btnDeleteSpecialist"))) {
            if (specialists.length > 0) {
                for (int i = 0; i < specialists.length; i++) {
                    try {
                        ProfessionalSpecialist specialist = professionalSpecialistDao.find(Integer.parseInt(specialists[i]));
                        if (specialist != null) {
                            specialist.setDeleted(true);
                            professionalSpecialistDao.merge(specialist);
                            LogAction.addLogSynchronous(LoggedInInfo.getLoggedInInfoFromSession(request),
                                    LogConst.DELETE, "specialist id=" + specialist.getId());
                        }
                    } catch (NumberFormatException e) {
                        MiscUtils.getLogger().warn("Invalid specialist ID: {}", Encode.forJava(specialists[i]), e);
                    }
                }
            }
            EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
            constructSpecialistsScriptsFile.makeString(request.getLocale());
            return "delete";
        }

        // not delete request, just update one entry
        ProfessionalSpecialist professionalSpecialist;
        try {
            professionalSpecialist = professionalSpecialistDao.find(Integer.parseInt(specId));
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("Invalid specialist ID for update: {}", Encode.forJava(specId), e);
            return ERROR;
        }
        if (professionalSpecialist == null) {
            MiscUtils.getLogger().warn("Specialist not found for ID: {}", Encode.forJava(specId));
            return ERROR;
        }

        int updater = 0;
        request.setAttribute("fName", professionalSpecialist.getFirstName());
        request.setAttribute("lName", professionalSpecialist.getLastName());
        request.setAttribute("proLetters", professionalSpecialist.getProfessionalLetters());

        request.setAttribute("address", professionalSpecialist.getStreetAddress());

        request.setAttribute("phone", professionalSpecialist.getPhoneNumber());
        request.setAttribute("fax", professionalSpecialist.getFaxNumber());
        request.setAttribute("website", professionalSpecialist.getWebSite());
        request.setAttribute("email", professionalSpecialist.getEmailAddress());
        request.setAttribute("specType", professionalSpecialist.getSpecialtyType());
        request.setAttribute("specId", specId);
        request.setAttribute("eDataUrl", professionalSpecialist.geteDataUrl());
        request.setAttribute("eDataOscarKey", professionalSpecialist.geteDataOscarKey());
        request.setAttribute("eDataServiceKey", professionalSpecialist.geteDataServiceKey());
        request.setAttribute("eDataServiceName", professionalSpecialist.geteDataServiceName());
        request.setAttribute("annotation", professionalSpecialist.getAnnotation());
        request.setAttribute("referralNo", professionalSpecialist.getReferralNo());
        request.setAttribute("institution", professionalSpecialist.getInstitutionId() != null
                ? professionalSpecialist.getInstitutionId().toString() : "");
        request.setAttribute("department", professionalSpecialist.getDepartmentId() != null
                ? professionalSpecialist.getDepartmentId().toString() : "");
        request.setAttribute("privatePhoneNumber", professionalSpecialist.getPrivatePhoneNumber());
        request.setAttribute("cellPhoneNumber", professionalSpecialist.getCellPhoneNumber());
        request.setAttribute("pagerNumber", professionalSpecialist.getPagerNumber());
        request.setAttribute("salutation", professionalSpecialist.getSalutation());
        request.setAttribute("hideFromView", professionalSpecialist.getHideFromView());
        request.setAttribute("eformId", professionalSpecialist.getEformId());

        request.setAttribute("upd", Integer.valueOf(updater));
        EctConConstructSpecialistsScriptsFile constructSpecialistsScriptsFile = new EctConConstructSpecialistsScriptsFile();
        request.setAttribute("verd", constructSpecialistsScriptsFile.makeFile());
        constructSpecialistsScriptsFile.makeString(request.getLocale());
        return SUCCESS;
    }

    public String getSpecId() {
        MiscUtils.getLogger().debug("getter Specid");
        if (specId == null)
            specId = new String();
        return specId;
    }

    public void setSpecId(String str) {
        MiscUtils.getLogger().debug("setter specId");
        specId = str;
    }

    public String getDelete() {
        MiscUtils.getLogger().debug("getter delete");
        if (delete == null)
            delete = new String();
        return delete;
    }

    public void setDelete(String str) {
        MiscUtils.getLogger().debug("setter delete");
        delete = str;
    }

    public String[] getSpecialists() {
        MiscUtils.getLogger().debug("getter specialists");
        if (specialists == null)
            specialists = new String[0];
        return specialists;
    }

    public void setSpecialists(String str[]) {
        MiscUtils.getLogger().debug("setter specialists");
        specialists = str;
    }

    String specId;
    String delete;
    String specialists[];
}
