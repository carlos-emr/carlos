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

import com.opensymphony.xwork2.ActionSupport;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.FlowSheetCustomizationDao;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.dao.MeasurementDaoImpl.SearchCriteria;
import ca.openosp.openo.commn.dao.SecRoleDao;
import ca.openosp.openo.commn.model.FlowSheetCustomization;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.commn.model.SecRole;
import ca.openosp.openo.commn.model.Validations;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ca.openosp.openo.encounter.data.EctProgram;
import ca.openosp.openo.encounter.oscarMeasurements.MeasurementFlowSheet;
import ca.openosp.openo.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EctMeasurements2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_measurement", "w",
                null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        boolean ajax = (request.getParameter("ajax") != null) ? Boolean.valueOf(request.getParameter("ajax")) : false;
        boolean skipCreateNote = (request.getParameter("skipCreateNote") != null)
                ? Boolean.valueOf(request.getParameter("skipCreateNote"))
                : false;

        HttpSession session = request.getSession();

        String demographicNo = request.getParameter("demographicNo");
        String providerNo = (String) session.getAttribute("user");
        String prog_no = new EctProgram(session).getProgram(providerNo);

        String template = request.getParameter("template");
        MeasurementFlowSheet mFlowsheet = null;
        if (template != null) {
            WebApplicationContext ctx = WebApplicationContextUtils
                    .getRequiredWebApplicationContext(session.getServletContext());
            FlowSheetCustomizationDao flowSheetCustomizationDao = (FlowSheetCustomizationDao) ctx
                    .getBean(FlowSheetCustomizationDao.class);
            MeasurementTemplateFlowSheetConfig templateConfig = MeasurementTemplateFlowSheetConfig.getInstance();

            List<FlowSheetCustomization> custList = flowSheetCustomizationDao.getFlowSheetCustomizations(template,
                    (String) session.getAttribute("user"), Integer.parseInt(demographicNo));
            mFlowsheet = templateConfig.getFlowSheet(template, custList);
        }

        String numType = request.getParameter("numType");
        int iType = Integer.parseInt(numType);

        String textOnEncounter = ""; // "**"+StringUtils.rightPad(by,80,"*")+"\\n";

        // if parent window content has changed then we need to propagate change so
        // we do not write to parent
        String parentChanged = request.getParameter("parentChanged");
        request.setAttribute("parentChanged", parentChanged);

        boolean valid = true;

        EctValidation ectValidation = new EctValidation();

        String inputValueName, inputTypeName, inputTypeDisplayName, mInstrcName, commentsName;
        String dateName, validationName, inputValue, inputType, inputTypeDisplay, mInstrc;
        String comments, dateObserved, validation;

        String regExp = null;
        Double dMax = Double.valueOf(0);
        Double dMin = Double.valueOf(0);
        Integer iMax = 0;
        Integer iMin = 0;
        Boolean numeric = null;

        List<Validations> vs = null;
        String regCharExp;
        // goes through each type to check if the input value is valid
        for (int i = 0; i < iType; i++) {
            inputValueName = "inputValue-" + i;
            inputTypeName = "inputType-" + i;
            inputTypeDisplayName = "inputTypeDisplayName-" + i;
            mInstrcName = "inputMInstrc-" + i;
            commentsName = "comments-" + i;
            dateName = "date-" + i;
            inputValue = request.getParameter(inputValueName);
            inputType = request.getParameter(inputTypeName);
            inputTypeDisplay = request.getParameter(inputTypeDisplayName);
            mInstrc = request.getParameter(mInstrcName);
            comments = request.getParameter(commentsName);
            dateObserved = request.getParameter(dateName);

            if (StringUtils.isEmpty(inputValue)) {
                continue;
            }

            regExp = null;
            dMax = Double.valueOf(0);
            dMin = Double.valueOf(0);
            iMax = 0;
            iMin = 0;

            vs = ectValidation.getValidationType(inputType, mInstrc);
            regCharExp = ectValidation.getRegCharacterExp();

            if (!vs.isEmpty()) {
                Validations v = vs.iterator().next();
                dMax = v.getMaxValue();
                dMin = v.getMinValue();
                iMax = v.getMaxLength();
                iMin = v.getMinLength();
                regExp = v.getRegularExp();
                numeric = v.isNumeric();
            }

            if (dMax == null) {
                dMax = Double.valueOf(0);
            }
            if (dMin == null) {
                dMin = Double.valueOf(0);
            }
            if (iMax == null) {
                iMax = 0;
            }
            if (iMin == null) {
                iMin = 0;
            }
            if (!ectValidation.isInRange(dMax, dMin, inputValue)) {
                addActionError(getText("errors.range", new String[]{inputTypeDisplay, Double.toString(dMin),
                        Double.toString(dMax)}));
                valid = false;
            }
            if (!ectValidation.maxLength(iMax, inputValue)) {
                addActionError(getText("errors.maxlength", new String[]{inputTypeDisplay, Integer.toString(iMax)}));
                valid = false;
            }
            if (!ectValidation.minLength(iMin, inputValue)) {
                addActionError(getText("errors.minlength", new String[]{inputTypeDisplay, Integer.toString(iMin)}));
                valid = false;
            }

            if (!ectValidation.matchRegExp(regExp, inputValue)) {
                addActionError(getText("errors.invalid", new String[]{inputTypeDisplay}));
                valid = false;
            }
            if (!ectValidation.isValidBloodPressure(regExp, inputValue)) {
                addActionError(getText("error.bloodPressure"));
                valid = false;
            }
            if (!ectValidation.isDate(dateObserved) && inputValue.compareTo("") != 0) {
                addActionError(getText("errors.invalidDate", new String[]{inputTypeDisplay}));
                valid = false;
            }
            if (!ectValidation.isNumeric(numeric, inputValue)) {
                addActionError(getText("errors.numeric", new String[]{inputTypeDisplay}));
                valid = false;
            }
        }

        // Write to database and to encounter form if all the input values are valid
        if (valid) {
            for (int i = 0; i < iType; i++) {

                inputValueName = "inputValue-" + i;
                inputTypeName = "inputType-" + i;
                mInstrcName = "inputMInstrc-" + i;
                commentsName = "comments-" + i;
                validationName = "validation-" + i;
                dateName = "date-" + i;

                inputValue = request.getParameter(inputValueName);
                inputType = request.getParameter(inputTypeName);
                mInstrc = request.getParameter(mInstrcName);
                comments = request.getParameter(commentsName);
                validation = request.getParameter(validationName);
                dateObserved = request.getParameter(dateName);

                org.apache.commons.validator.GenericValidator gValidator = new org.apache.commons.validator.GenericValidator();
                if (!GenericValidator.isBlankOrNull(inputValue)) {

                    // Find if the same data has already been entered into the system
                    MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
                    SearchCriteria sc = new SearchCriteria();
                    sc.setDemographicNo(demographicNo);
                    sc.setDataField(inputValue);
                    sc.setMeasuringInstrc(mInstrc);
                    sc.setComments(comments);
                    sc.setDateObserved(ConversionUtils.fromDateString(dateObserved));
                    sc.setType(inputType);
                    List<Measurement> ms = dao.find(sc);

                    if (ms.isEmpty()) {
                        // Write to the Dababase if all input values are valid
                        Measurement m = new Measurement();
                        m.setType(inputType);
                        m.setDemographicId(Integer.parseInt(demographicNo));
                        m.setProviderNo(providerNo);
                        m.setDataField(inputValue);
                        m.setMeasuringInstruction(mInstrc);
                        m.setComments(comments);
                        m.setDateObserved(ConversionUtils.fromDateString(dateObserved));
                        m.setAppointmentNo(0);
                        dao.persist(m);

                        // prepare input values for writing to the encounter form
                        if (mFlowsheet == null) {
                            textOnEncounter = textOnEncounter + inputType + "    " + inputValue + " " + mInstrc + " "
                                    + comments + "\n";
                        } else {
                            textOnEncounter += mFlowsheet.getFlowSheetItem(inputType).getDisplayName() + "    "
                                    + inputValue + " " + comments + "\n";
                        }
                    }
                }

            }
            // textOnEncounter = textOnEncounter +
            // "**********************************************************************************\\n";

        } else {
            String groupName = request.getParameter("groupName");
            String css = request.getParameter("css");
            request.setAttribute("groupName", groupName);
            request.setAttribute("css", css);
            request.setAttribute("demographicNo", demographicNo);

            if (ajax) {
                ObjectNode obj = objectMapper.createObjectNode();
                ArrayNode errorObj = objectMapper.createArrayNode();
                for (String error : getActionErrors()) {
                    if (error != null && !error.trim().isEmpty()) {
                        errorObj.add(error);
                    }
                }
                obj.set("errors", errorObj);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(obj.toString());
                return null;
            }

            response.sendRedirect(request.getContextPath() + "/oscarEncounter/oscarMeasurements/AddMeasurementData.jsp");
            return NONE;
        }

        if (valid && !skipCreateNote) {
            // create note
            CaseManagementManager cmm = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);

            SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
            SecRole doctorRole = secRoleDao.findByName("doctor");
            String reporter_caisi_role = doctorRole.getId().toString();

            SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy");
            Date date = new Date();
            String formattedDate = "[" + df.format(date) + " .: ]";
            textOnEncounter = formattedDate + "\n" + textOnEncounter;

            CaseManagementNote cmn = new CaseManagementNote();
            cmn.setUpdate_date(date);
            cmn.setObservation_date(date);
            cmn.setDemographic_no(demographicNo);
            cmn.setProviderNo(providerNo);
            cmn.setNote(textOnEncounter);
            cmn.setSigned(true);
            cmn.setSigning_provider_no(providerNo);
            cmn.setProgram_no(prog_no);
            cmn.setReporter_caisi_role(reporter_caisi_role);

            cmn.setReporter_program_team("0");
            cmn.setPassword("NULL");
            cmn.setLocked(false);
            cmn.setHistory(textOnEncounter + "-----hi story----");
            cmn.setPosition(0);
            cmn.setAppointmentNo(0);

            cmm.saveNoteSimple(cmn);

        } // create note

        if (ajax) {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("encounterText", textOnEncounter);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            objectMapper.writeValue(response.getWriter(), json);
            return null;
        } else {
            request.setAttribute("textOnEncounter", StringEscapeUtils.escapeEcmaScript(textOnEncounter));
            return SUCCESS;
        }
    }
}
