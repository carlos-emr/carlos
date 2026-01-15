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


package ca.openosp.openo.demographic.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.CtlRelationshipsDao;
import ca.openosp.openo.commn.model.CtlRelationships;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SessionConstants;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.demographic.data.DemographicRelationship;
// TODO STRUTS2 - not sure if we need the servlet, thinking it is still needed so left it with the merge. Review if issues.

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AddDemographicRelationship2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public AddDemographicRelationship2Action() {

    }

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        String origDemo = request.getParameter("origDemo");
        String linkingDemo = request.getParameter("linkingDemo");
        String relation = request.getParameter("relation");
        String sdm = request.getParameter("sdm");
        String emergContact = request.getParameter("emergContact");
        String notes = request.getParameter("notes");

	    if (origDemo != null && origDemo.matches("[a-zA-Z0-9]+")) {
        request.setAttribute("demographicNo", origDemo);
	    }

        if (request.getParameter("pmmClient") != null && request.getParameter("pmmClient").equals("Finished")) {
            return "pmmClient";
        }

        String providerNo = (String) request.getSession().getAttribute("user");

        boolean sdmBool = false;
        boolean eBool = false;
        if (sdm != null && sdm.equals("yes")) {
            sdmBool = true;
        }
        if (emergContact != null && emergContact.equals("yes")) {
            eBool = true;
        }

        // if we're in a facility tag this association with the facility
        Facility facility = (Facility) request.getSession().getAttribute(SessionConstants.CURRENT_FACILITY);
        Integer facilityId = null;
        if (facility != null) facilityId = facility.getId();

        DemographicRelationship demo = new DemographicRelationship();
        demo.addDemographicRelationship(origDemo, linkingDemo, relation, sdmBool, eBool, notes, providerNo, facilityId);

	    if (origDemo != null && origDemo.matches("[a-zA-Z0-9]+")) {
		    request.setAttribute("demo", origDemo);
	    }
        // ***** NEW CODE *****
        // Now link in the opposite direction
        // First work out which pairs match up
        // From AddAlternateConcact.jsp

        // Relations for the dropdowns should be stored in a table in the database and not hardcoded

         /*
         This is better:

                Parent
                StepParent
                Child
                Sibling
                Spouse
                Partner
                Grandparent
                Other Relative
                Other

         Sex will determine whether it is a brother,
        grandfather, wife, husband or spouse of the same sex
          */

        boolean relationset = false;

        CtlRelationshipsDao ctlRelationshipsDao = SpringUtils.getBean(CtlRelationshipsDao.class);
        CtlRelationships cr = ctlRelationshipsDao.findByValue(relation);
        if (cr != null && ((cr.getMaleInverse() != null && cr.getMaleInverse().length() > 0) || (cr.getFemaleInverse() != null && cr.getFemaleInverse().length() > 0))) {
            //need sex of the relation
            Demographic d = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), origDemo);
            if (d != null && d.getSex().equalsIgnoreCase("M")) {
                relation = cr.getMaleInverse();
                relationset = true;
            }
            if (d != null && d.getSex().equalsIgnoreCase("F")) {
                relation = cr.getFemaleInverse();
                relationset = true;
            }

        }


        if (relationset) {
            // flip the demographics
            String tempdemo;
            tempdemo = origDemo;
            origDemo = linkingDemo;
            linkingDemo = tempdemo;

            //now save this
            DemographicRelationship demo2 = new DemographicRelationship();
            demo2.addDemographicRelationship(origDemo, linkingDemo, relation, sdmBool, eBool, notes, providerNo, facilityId);
        }


        return SUCCESS;
    }

}
