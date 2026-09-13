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

    /**
     * Renders the "Add Relation" contact-search/save form, or persists a new demographic
     * relationship when the request is a genuine save.
     *
     * <p>Request-method contract: a bare GET (only {@code demo}/{@code origDemo} present) renders
     * the form and never mutates. A request carrying non-blank {@code linkingDemo} and
     * {@code relation} is treated as save intent and MUST be a POST — a non-POST save attempt is
     * rejected with {@code 405} (and an {@code Allow: POST} header) before any DAO call. A POST
     * save with a missing or non-numeric {@code origDemo}/{@code linkingDemo} is rejected with
     * {@code 400} rather than persisting a relationship against demographic {@code 0}.</p>
     *
     * @return {@link #SUCCESS} to render/re-render the form (including after a successful save),
     *         {@code "pmmClient"} when the request is the PMM client-finished callback, or
     *         {@link #NONE} after writing a {@code 405}/{@code 400} error response directly
     * @throws SecurityException if the caller lacks {@code _demographic w}
     * @throws IOException if writing the {@code 405}/{@code 400} error response fails
     * @since 2005-10-05
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws IOException {

        LoggedInInfo loggedInInfo = LoggedInInfo.requireLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
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
            request.setAttribute("demo", origDemo);
        }

        // Creating a relationship is a mutation and must arrive via POST. The "Add Relation"
        // popup (edit-view.jsp) opens this action with a plain GET carrying only `demo` to
        // render the contact-search form (AddAlternateContact.jsp) — linkingDemo/relation are
        // only present once the form is actually submitted. Gating on their presence (rather
        // than method alone) also lets the intermediate "select a contact from search results"
        // POST step render without persisting a relationship row before the user has chosen one.
        // This check runs before the pmmClient short-circuit below so a non-POST request cannot
        // use pmmClient=Finished to slip a save attempt past the method gate.
        boolean isMutation = isNonBlank(linkingDemo) && isNonBlank(relation);
        if (isMutation && !"POST".equalsIgnoreCase(request.getMethod())) {
            // RFC 7231 §6.5.5: 405 responses MUST include the Allow header.
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if ("Finished".equals(request.getParameter("pmmClient"))) {
            return "pmmClient";
        }

        if (!isMutation) {
            return SUCCESS;
        }

        // origDemo/linkingDemo must be real demographic numbers before they reach persistence:
        // ConversionUtils.fromIntString coerces a missing or non-numeric value to 0, which would
        // otherwise persist a relationship row pointing at demographic 0.
        if (!isValidDemographicNo(origDemo) || !isValidDemographicNo(linkingDemo)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "origDemo and linkingDemo must be valid demographic numbers");
            return NONE;
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

        InverseRelation inverse = computeInverseRelation(origDemo, linkingDemo, relation);
        if (inverse != null) {
            DemographicRelationship demo2 = new DemographicRelationship();
            demo2.addDemographicRelationship(inverse.origDemo(), inverse.linkingDemo(), inverse.relation(),
                    sdmBool, eBool, notes, providerNo, facilityId);
        }

        return SUCCESS;
    }

    // A digit-only string that overflows int (e.g. "2147483648") still coerces to 0 in
    // ConversionUtils.fromIntString via the caught NumberFormatException, so the digit check
    // alone isn't enough -- confirm it actually parses as an int before accepting it. "0" is
    // rejected too: demographic_no is an auto-increment PK starting at 1, so 0 is never a real
    // patient -- it's the exact placeholder fromIntString(null/blank) coerces to.
    private static boolean isValidDemographicNo(String demographicNo) {
        if (demographicNo == null || !demographicNo.matches("^\\d+$")) {
            return false;
        }
        try {
            return Integer.parseInt(demographicNo) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Blank (as opposed to absent) linkingDemo/relation must not count as mutation intent either —
    // ConversionUtils.fromIntString("") coerces to 0 the same as fromIntString(null), so treating a
    // blank value as "real" would persist the same garbage relationship row this gate exists to stop.
    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    // Relations for the dropdowns should be stored in a table in the database and not hardcoded.
    // Sex determines whether the inverse is e.g. brother/sister, grandfather/grandmother,
    // husband/wife of the same relation (from AddAlternateContact.jsp's original logic).
    // Returns null when no inverse relation applies (e.g. relation type has no sex-specific inverse).
    private InverseRelation computeInverseRelation(String origDemo, String linkingDemo, String relation) {
        boolean relationset = false;

        CtlRelationshipsDao ctlRelationshipsDao = SpringUtils.getBean(CtlRelationshipsDao.class);
        CtlRelationships cr = ctlRelationshipsDao.findByValue(relation);
        if (cr != null && ((cr.getMaleInverse() != null && cr.getMaleInverse().length() > 0) || (cr.getFemaleInverse() != null && cr.getFemaleInverse().length() > 0))) {
            //need sex of the relation
            Demographic d = demographicManager.getDemographic(loggedInInfo, origDemo);
            if (d != null && d.getSex().equalsIgnoreCase("M")) {
                relation = cr.getMaleInverse();
                relationset = true;
            }
            if (d != null && d.getSex().equalsIgnoreCase("F")) {
                relation = cr.getFemaleInverse();
                relationset = true;
            }
        }

        if (!relationset) {
            return null;
        }
        // flip the demographics
        return new InverseRelation(linkingDemo, origDemo, relation);
    }

    private record InverseRelation(String origDemo, String linkingDemo, String relation) {
    }

}
