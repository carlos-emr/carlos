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


package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.model.CtlRelationships;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.demographic.data.DemographicRelationship;
// TODO STRUTS2 - not sure if we need the servlet, thinking it is still needed so left it with the merge. Review if issues.

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class AddDemographicRelationship2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public AddDemographicRelationship2Action() {

    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        String origDemo = request.getParameter("origDemo");
        String linkingDemo = request.getParameter("linkingDemo");
        String relation = request.getParameter("relation");
        String sdm = request.getParameter("sdm");
        String emergContact = request.getParameter("emergContact");
        String notes = request.getParameter("notes");

        if (isValidDemographicNo(origDemo)) {
            request.setAttribute("demographicNo", origDemo);
        }

        if ("Finished".equals(request.getParameter("pmmClient"))) {
            return "pmmClient";
        }

        // Creating a relationship is a mutation and must arrive via POST. The "Add Relation"
        // popup (edit-view.jsp) opens this action with a plain GET carrying only `demo` to
        // render the contact-search form (AddAlternateContact.jsp) — linkingDemo/relation are
        // only present once the form is actually submitted. Gating on their presence (rather
        // than method alone) also lets the intermediate "select a contact from search results"
        // POST step render without persisting a relationship row before the user has chosen one.
        boolean isMutation = linkingDemo != null && relation != null;
        if (isMutation && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        if (!isMutation) {
            return SUCCESS;
        }

        String providerNo = (String) request.getSession().getAttribute("user");

        boolean sdmBool = "yes".equals(sdm);
        boolean eBool = "yes".equals(emergContact);

        // if we're in a facility tag this association with the facility
        Facility facility = (Facility) request.getSession().getAttribute(SessionConstants.CURRENT_FACILITY);
        Integer facilityId = null;
        if (facility != null) facilityId = facility.getId();

        DemographicRelationship demo = new DemographicRelationship();
        demo.addDemographicRelationship(origDemo, linkingDemo, relation, sdmBool, eBool, notes, providerNo, facilityId);

        if (isValidDemographicNo(origDemo)) {
            request.setAttribute("demo", origDemo);
        }

        linkInverseRelationship(origDemo, linkingDemo, relation, sdmBool, eBool, notes, providerNo, facilityId);

        return SUCCESS;
    }

    private static boolean isValidDemographicNo(String demographicNo) {
        return demographicNo != null && demographicNo.matches("[a-zA-Z0-9]+");
    }

    // Relations for the dropdowns should be stored in a table in the database and not hardcoded.
    // Sex determines whether the inverse is e.g. brother/sister, grandfather/grandmother,
    // husband/wife of the same relation (from AddAlternateContact.jsp's original logic).
    private void linkInverseRelationship(String origDemo, String linkingDemo, String relation, boolean sdmBool,
            boolean eBool, String notes, String providerNo, Integer facilityId) {
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
            String tempdemo = origDemo;
            origDemo = linkingDemo;
            linkingDemo = tempdemo;

            //now save this
            DemographicRelationship demo2 = new DemographicRelationship();
            demo2.addDemographicRelationship(origDemo, linkingDemo, relation, sdmBool, eBool, notes, providerNo, facilityId);
        }
    }

}
