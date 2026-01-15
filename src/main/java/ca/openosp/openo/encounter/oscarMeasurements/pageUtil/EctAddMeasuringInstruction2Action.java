//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.commn.dao.MeasurementTypeDao;
import ca.openosp.openo.commn.model.MeasurementType;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctTypeDisplayNameBeanHandler;
import ca.openosp.openo.encounter.oscarMeasurements.bean.EctValidationsBeanHandler;
import ca.openosp.openo.messenger.util.MsgStringQuote;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctAddMeasuringInstruction2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {
            MsgStringQuote str = new MsgStringQuote();
            String requestId = "";
            List messages = new LinkedList();

            String typeDisplayName = this.getTypeDisplayName();
            String measuringInstrc = this.getMeasuringInstrc();
            String validation = this.getValidation();

            boolean isValid = true;

            EctValidation validate = new EctValidation();
            String regExp = validate.getRegCharacterExp();
            String errorField = "The measuring instruction " + measuringInstrc;
            String contextPath = request.getContextPath();
            if (!validate.matchRegExp(regExp, measuringInstrc)) {
                addActionError(getText("errors.invalid", new String[]{errorField}));
                isValid = false;
            }
            if (!validate.maxLength(255, measuringInstrc)) {
                addActionError(getText("errors.maxlength", new String[]{errorField, "255"}));
                isValid = false;
            }
            if (!isValid) {
                response.sendRedirect(contextPath + "/oscarEncounter/oscarMeasurements/AddMeasuringInstruction.jsp");
                return NONE;
            }

            List<MeasurementType> mts = dao.findByMeasuringInstructionAndTypeDisplayName(measuringInstrc, typeDisplayName);
            if (mts.size() > 0) {
                addActionError(getText("error.oscarEncounter.Measurements.duplicateTypeName"));
                response.sendRedirect(contextPath + "/oscarEncounter/oscarMeasurements/AddMeasuringInstruction.jsp");
                return NONE;
            }

            mts = dao.findByTypeDisplayName(typeDisplayName);
            if (mts.size() > 0) {
                MeasurementType mt = mts.get(0);
                String type = mt.getType();
                String typeDesc = mt.getTypeDescription();

                MeasurementType m = new MeasurementType();
                m.setType(type);
                m.setTypeDisplayName(typeDisplayName);
                m.setTypeDescription(typeDesc);
                m.setMeasuringInstruction(measuringInstrc);
                m.setValidation(validation);

                dao.persist(m);

                requestId = m.getId().toString();
            }

            String msg = getText("oscarEncounter.oscarMeasurements.AddMeasuringInstruction.successful", "!");
            messages.add(msg);
            request.setAttribute("messages", messages);

            EctTypeDisplayNameBeanHandler typeHd = new EctTypeDisplayNameBeanHandler();
            Collection typeDisplayNameList = typeHd.getTypeDisplayNameVector();

            EctValidationsBeanHandler validationHd = new EctValidationsBeanHandler();
            Collection validationsList = validationHd.getValidationsVector();

            HttpSession session = request.getSession();
            session.setAttribute("typeDisplayNames", typeDisplayNameList);
            session.setAttribute("validations", validationsList);

            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }

    }

    String typeDisplayName;
    String measuringInstrc;
    String validation;

    public String getTypeDisplayName() {
        return this.typeDisplayName;
    }

    public void setTypeDisplayName(String typeDisplayName) {
        this.typeDisplayName = typeDisplayName;
    }

    public String getMeasuringInstrc() {
        return this.measuringInstrc;
    }

    public void setMeasuringInstrc(String measuringInstrc) {
        this.measuringInstrc = measuringInstrc;
    }

    public String getValidation() {
        return this.validation;
    }

    public void setValidation(String validation) {
        this.validation = validation;
    }


}
