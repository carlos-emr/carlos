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
import java.util.LinkedList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctAddMeasurementType2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();


    private MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws ServletException, IOException {
        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {
            List<String> messages = new LinkedList<String>();

            String type = this.getType();
            String typeUp = type.toUpperCase();
            String typeDesc = this.getTypeDesc();
            String typeDisplayName = this.getTypeDisplayName();
            String measuringInstrc = this.getMeasuringInstrc();
            String validation = this.getValidation();
            if (!allInputIsValid(request, type, typeDesc, typeDisplayName, measuringInstrc)) {
                return "failure";
            }

            MeasurementType mt = new MeasurementType();
            mt.setType(typeUp);
            mt.setTypeDescription(typeDesc);
            mt.setTypeDisplayName(typeDisplayName);
            mt.setMeasuringInstruction(measuringInstrc);
            mt.setValidation(validation);
            dao.persist(mt);


            String msg = getText("encounter.oscarMeasurements.AddMeasurementType.successful", "!");
            messages.add(msg);
            request.setAttribute("messages", messages);
            MeasurementTypes mts = MeasurementTypes.getInstance();
            mts.reInit();
            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }

    }

    private boolean allInputIsValid(HttpServletRequest request, String type, String typeDesc, String typeDisplayName, String measuringInstrc) {

        EctValidation validate = new EctValidation();
        String regExp = validate.getRegCharacterExp();
        boolean isValid = true;

        for (MeasurementType mt : dao.findByType(type)) {
            addActionError(getText("error.encounter.Measurements.duplicateTypeName"));
            isValid = false;
        }

        String errorField = "The type " + type;
        if (!validate.matchRegExp(regExp, type)) {
            addActionError(getText("errors.invalid", errorField));
            isValid = false;
        }
        if (!validate.maxLength(50, type)) {
            addActionError(getText("errors.maxlength", new String[]{errorField, "4"}));
            isValid = false;
        }

        errorField = "The type description " + typeDesc;
        if (!validate.matchRegExp(regExp, typeDesc)) {
            addActionError(getText("errors.invalid", errorField));
            isValid = false;
        }
        if (!validate.maxLength(255, type)) {
            addActionError(getText("errors.maxlength", new String[]{errorField, "255"}));
            isValid = false;
        }

        errorField = "The display name " + typeDisplayName;
        if (!validate.matchRegExp(regExp, typeDisplayName)) {
            addActionError(getText("errors.invalid", errorField));
            isValid = false;
        }
        if (!validate.maxLength(255, type)) {
            addActionError(getText("errors.maxlength", new String[]{errorField, "255"}));
            isValid = false;
        }

        errorField = "The measuring instruction " + measuringInstrc;
        if (!validate.matchRegExp(regExp, measuringInstrc)) {
            addActionError(getText("errors.invalid", errorField));
            isValid = false;
        }
        if (!validate.maxLength(255, type)) {
            addActionError(getText("errors.maxlength", new String[]{errorField, "255"}));
            isValid = false;
        }
        return isValid;
    }


    String type;
    String typeDesc;
    String typeDisplayName;
    String measuringInstrc;
    String validation;

    public String getType() {
        return this.type;
    }

    @StrutsParameter
    public void setType(String type) {
        this.type = type;
    }


    public String getTypeDesc() {
        return this.typeDesc;
    }

    @StrutsParameter
    public void setTypeDesc(String typeDesc) {
        this.typeDesc = typeDesc;
    }

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
