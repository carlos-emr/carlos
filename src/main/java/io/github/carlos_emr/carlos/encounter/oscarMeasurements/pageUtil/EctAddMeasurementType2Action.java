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
 * <p>
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts2 action for adding a new custom measurement type to the system.
 * Validates all input fields (type code, description, display name, measuring instruction),
 * checks for duplicates, and persists the new {@link MeasurementType} record.
 *
 * @since 2004-02-23
 */
public class EctAddMeasurementType2Action extends ActionSupport {
    /**
     * Width of the {@code typeDescription}, {@code typeDisplayName} and {@code measuringInstruction}
     * columns of {@code measurementType}: VARCHAR(255) in the V1 baseline schema.
     */
    private static final int TEXT_COLUMN_MAX_LENGTH = 255;
    private static final String ERRORS_INVALID = "errors.invalid";
    private static final String ERRORS_MAXLENGTH = "errors.maxlength";

    HttpServletRequest request = ServletActionContext.getRequest();


    private MeasurementTypeDao dao = SpringUtils.getBean(MeasurementTypeDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // FindSecBugs IMPROPER_UNICODE: Locale.ROOT upper-casing gives the measurement type code one stored and looked-up
    // form; it is not a security or authorization decision, and only codes matching the ASCII-only pattern are stored.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Locale.ROOT upper-casing gives the measurement type code one stored and looked-up form; not a security or authorization decision")
    public String execute()
            throws ServletException, IOException {
        // Writes only from the form's POST: a link, image tag or prefetch must never change the
        // measurement configuration (issue #3335).
        if (!"POST".equals(request.getMethod())) {
            HttpServletResponse response = ServletActionContext.getResponse();
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {
            List<String> messages = new LinkedList<String>();

            String type = this.getType();
            if (type == null || type.isEmpty()) {
                addActionError(getText(ERRORS_INVALID, new String[]{"Measurement type is required"}));
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
                return "failure";
            }
            String typeUp = type.toUpperCase(Locale.ROOT);
            String typeDesc = this.getTypeDesc();
            String typeDisplayName = this.getTypeDisplayName();
            String measuringInstrc = this.getMeasuringInstrc();
            String validation = this.getValidation();
            if (!allInputIsValid(type, typeUp, typeDesc, typeDisplayName, measuringInstrc)) {
                request.setAttribute("actionErrors", new java.util.ArrayList<>(getActionErrors()));
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
            throw new SecurityException("missing required sec object (_admin.measurements)");
        }

    }

    private boolean allInputIsValid(String type, String typeUp, String typeDesc, String typeDisplayName, String measuringInstrc) {

        EctValidation validate = new EctValidation();
        String regExp = validate.getRegCharacterExp();
        boolean isValid = true;

        if (!dao.findByType(typeUp).isEmpty()) {
            addActionError(getText("error.encounter.Measurements.duplicateTypeName"));
            isValid = false;
        }

        String errorField = "The type " + type;
        if (!validate.matchRegExp(regExp, type)) {
            addActionError(getText(ERRORS_INVALID, errorField));
            isValid = false;
        }
        if (!validate.maxLength(50, typeUp)) {
            addActionError(getText(ERRORS_MAXLENGTH, new String[]{errorField, "50"}));
            isValid = false;
        }

        errorField = "The type description " + typeDesc;
        if (!validate.matchRegExp(regExp, typeDesc)) {
            addActionError(getText(ERRORS_INVALID, errorField));
            isValid = false;
        }
        if (!checkMaxLength(validate, errorField, typeDesc, TEXT_COLUMN_MAX_LENGTH)) {
            isValid = false;
        }

        errorField = "The display name " + typeDisplayName;
        if (!validate.matchRegExp(regExp, typeDisplayName)) {
            addActionError(getText(ERRORS_INVALID, errorField));
            isValid = false;
        }
        if (!checkMaxLength(validate, errorField, typeDisplayName, TEXT_COLUMN_MAX_LENGTH)) {
            isValid = false;
        }

        errorField = "The measuring instruction " + measuringInstrc;
        if (!validate.matchRegExp(regExp, measuringInstrc)) {
            addActionError(getText(ERRORS_INVALID, errorField));
            isValid = false;
        }
        if (!checkMaxLength(validate, errorField, measuringInstrc, TEXT_COLUMN_MAX_LENGTH)) {
            isValid = false;
        }
        return isValid;
    }

    /**
     * Checks one submitted value against the width of the {@code measurementType} column that
     * stores it, and reports the field on the form when the value is too long.
     *
     * @param validate   the validator shared by this submission's checks
     * @param errorField how the form names the field: its label followed by the submitted text.
     *                   The form prints action errors as markup, so it is HTML-encoded here.
     * @param value      the submitted text of that same field; {@code null} has no length to check
     * @param maxLength  the width of the column that stores the value
     * @return {@code true} if the value fits its column
     */
    private boolean checkMaxLength(EctValidation validate, String errorField, String value, int maxLength) {
        // EctValidation.maxLength dereferences its input, and a request may omit a parameter.
        if (value == null || validate.maxLength(maxLength, value)) {
            return true;
        }
        addActionError(getText(ERRORS_MAXLENGTH,
                new String[]{SafeEncode.forHtmlContent(errorField), String.valueOf(maxLength)}));
        return false;
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
