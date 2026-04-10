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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctTypeDisplayNameBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBeanHandler;
import io.github.carlos_emr.carlos.messenger.util.MsgStringQuote;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for adding a new measuring instruction to an existing measurement type.
 * Validates the instruction text, checks for duplicates against the specified type display name,
 * and persists a new {@link MeasurementType} record with the instruction.
 *
 * @since 2004-02-23
 */
public class EctAddMeasuringInstruction2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();


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

            if (typeDisplayName == null || typeDisplayName.isEmpty() || measuringInstrc == null || measuringInstrc.isEmpty()) {
                addActionError(getText("errors.invalid", new String[]{"Display name and measuring instruction are required"}));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return "failure";
            }

            boolean isValid = true;

            EctValidation validate = new EctValidation();
            String regExp = validate.getRegCharacterExp();
            String errorField = "The measuring instruction " + measuringInstrc;
            if (!validate.matchRegExp(regExp, measuringInstrc)) {
                addActionError(getText("errors.invalid", new String[]{errorField}));
                isValid = false;
            }
            if (!validate.maxLength(255, measuringInstrc)) {
                addActionError(getText("errors.maxlength", new String[]{errorField, "255"}));
                isValid = false;
            }
            if (!isValid) {
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return "failure";
            }

            List<MeasurementType> mts = dao.findByMeasuringInstructionAndTypeDisplayName(measuringInstrc, typeDisplayName);
            if (mts.size() > 0) {
                addActionError(getText("error.encounter.Measurements.duplicateTypeName"));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return "failure";
            }

            mts = dao.findByTypeDisplayName(typeDisplayName);
            if (mts.isEmpty()) {
                addActionError(getText("errors.invalid", new String[]{"The display name " + typeDisplayName + " (no matching measurement type found)"}));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return "failure";
            }

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

            String msg = getText("encounter.oscarMeasurements.AddMeasuringInstruction.successful", "!");
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

    @StrutsParameter
    public void setTypeDisplayName(String typeDisplayName) {
        this.typeDisplayName = typeDisplayName;
    }

    public String getMeasuringInstrc() {
        return this.measuringInstrc;
    }

    @StrutsParameter
    public void setMeasuringInstrc(String measuringInstrc) {
        this.measuringInstrc = measuringInstrc;
    }

    public String getValidation() {
        return this.validation;
    }

    @StrutsParameter
    public void setValidation(String validation) {
        this.validation = validation;
    }


}
