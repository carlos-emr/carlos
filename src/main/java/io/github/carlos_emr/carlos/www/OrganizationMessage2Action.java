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

package io.github.carlos_emr.carlos.www;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.services.OrganizationMessageManager;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityMessageDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.FacilityMessage;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

/**
 * Struts 2 action for managing facility-level organization messages.
 *
 * <p>Provides list, edit, save, and view operations for facility messages that
 * can be targeted to specific programs. Messages are scoped to the user's
 * current facility and optionally associated with a specific program.
 *
 * @since 2012-08-13
 */
public class OrganizationMessage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private OrganizationMessageManager mgr = SpringUtils.getBean(OrganizationMessageManager.class);
    private FacilityDao facilityDao = SpringUtils.getBean(FacilityDao.class);
    private FacilityMessageDao facilityMessageDao = SpringUtils.getBean(FacilityMessageDao.class);
    private ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
    private ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);

    /**
     * Routes the request to the appropriate handler based on the "method" parameter.
     *
     * @return String the Struts result name
     */
    public String execute() {
        String mtd = request.getParameter("method");
        if ("edit".equals(mtd)) {
            return edit();
        } else if ("save".equals(mtd)) {
            return save();
        } else if ("view".equals(mtd)) {
            return view();
        }
        return list();
    }

    /**
     * Lists all active facility messages for the current facility, enriched with program names.
     *
     * @return String the "list" result name
     */
    public String list() {
        //List activeMessages = mgr.getMessages();
        Facility facility = (Facility) request.getSession().getAttribute("currentFacility");
        Integer facilityId = null;
        if (facility != null)
            facilityId = facility.getId();

        List<FacilityMessage> activeMessages = mgr.getMessagesByFacilityIdOrNull(facilityId);

        for (FacilityMessage msg : activeMessages) {
            if (msg.getProgramId() != null) {
                Program program = programManager2.getProgram(LoggedInInfo.getLoggedInInfoFromSession(request), msg.getProgramId());
                if (program != null) {
                    msg.setProgramName(program.getName());
                } else {
                    msg.setProgramName("N/A");
                }
            } else {
                msg.setProgramName("N/A");
            }
        }
        if (activeMessages != null && activeMessages.size() > 0)
            request.setAttribute("ActiveFacilityMessages", activeMessages);
        return "list";
    }

    /**
     * Loads a facility message for editing, or prepares a blank form for creating a new one.
     *
     * @return String the "edit" result name, or the list result if the message is not found
     */
    public String edit() {
        String messageId = request.getParameter("id");

        //List facilities = programProviderDAO.getFacilitiesInProgramDomain(providerNo);
        List<Facility> facilities = new ArrayList<Facility>();
        facilities.add((Facility) request.getSession().getAttribute("currentFacility"));

        request.getSession().setAttribute("facilities", facilities);

        List<Program> programs = programManager.getPrograms(((Facility) request.getSession().getAttribute("currentFacility")).getId());

        request.setAttribute("programs", programs);


        if (messageId != null) {
            FacilityMessage msg = mgr.getMessage(messageId);

            if (msg == null) {
                addActionMessage(getText("system_message.missing"));
                return list();
            }
            this.setFacility_message(msg);
        }


        return "edit";
    }

    /**
     * Saves a facility message with the current date and facility name.
     *
     * @return String the list result after saving
     */
    public String save() {
        FacilityMessage msg = this.getFacility_message();
        msg.setCreationDate(new Date());
        Integer facilityId = msg.getFacilityId().intValue();
        String facilityName = "";
        if (facilityId != null && facilityId.intValue() != 0)
            facilityName = facilityDao.find(facilityId).getName();
        msg.setFacilityName(facilityName);

        mgr.saveFacilityMessage(msg);

        addActionMessage(getText("system_message.saved"));
        return list();
    }

    /**
     * Displays facility messages filtered by the current facility and program context.
     *
     * @return String the "view" result name
     */
    public String view() {

        //String providerNo = (String)request.getSession().getAttribute("user");
        //List messages = programProviderDAO.getFacilityMessagesInProgramDomain(providerNo);
        Facility facility = (Facility) request.getSession().getAttribute("currentFacility");
        Integer facilityId = null;
        if (facility != null)
            facilityId = facility.getId();
        Integer programId = null;
        ProgramProvider pp = programManager2.getCurrentProgramInDomain(LoggedInInfo.getLoggedInInfoFromSession(request), LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo());
        if (pp != null) {
            programId = pp.getProgramId().intValue();
        }
        List<FacilityMessage> messages = facilityMessageDao.getMessagesByFacilityIdOrNullAndProgramIdOrNull(facilityId, programId);
        if (messages != null && messages.size() > 0) {
            request.setAttribute("FacilityMessages", messages);
        }
        return "view";
    }

    private FacilityMessage facility_message;

    /**
     * Returns the facility message model bound from the request form.
     *
     * @return FacilityMessage the facility message
     */
    @StrutsParameter(depth = 1)
    public FacilityMessage getFacility_message() {
        return facility_message;
    }

    /**
     * Sets the facility message model from the request form.
     *
     * @param facility_message FacilityMessage the facility message to set
     */
    @StrutsParameter
    public void setFacility_message(FacilityMessage facility_message) {
        this.facility_message = facility_message;
    }
}
