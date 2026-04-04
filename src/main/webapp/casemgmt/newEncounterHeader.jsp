<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo, io.github.carlos_emr.carlos.commn.model.Facility" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Clinic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ClinicDAO" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendRedirect("error.jsp");
        return;
    }

    Facility facility = loggedInInfo.getCurrentFacility();
    String demoNo = bean.demographicNo;
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = demographicManager.getDemographicWithExt(loggedInInfo, Integer.parseInt(demoNo));

    // Provider and clinic data for the copy-to-clipboard feature
    Provider loggedInProvider = loggedInInfo.getLoggedInProvider();
    ClinicDAO clinicDao = SpringUtils.getBean(ClinicDAO.class);
    Clinic clinic = clinicDao.getClinic();
    Provider mrp = demographic.getMrp();

    // this is accessed in the newEncounterLayout after this header is included.
    String privateConsentEnabledProperty = CarlosProperties.getInstance().getProperty("privateConsentEnabled");
    boolean privateConsentEnabled = privateConsentEnabledProperty != null && privateConsentEnabledProperty.equals("true");

%>

<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<script type="text/javascript">
/**
 * Writes text to the clipboard and shows brief visual feedback on the trigger element.
 * Uses the modern Clipboard API when available, falling back to execCommand('copy')
 * for older browsers.
 *
 * @param {string} text - The text to copy to the clipboard
 * @param {HTMLElement} el - The element that triggered the copy; its title and opacity
 *        are temporarily changed to indicate success
 */
function copyToClip(text, el) {
    var orig = el.title;
    function showFeedback() {
        el.title = 'Copied!';
        el.style.opacity = '0.5';
        setTimeout(function() { el.style.opacity = '1'; el.title = orig; }, 600);
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(showFeedback).catch(function() {
            fallbackCopy(text);
            showFeedback();
        });
    } else {
        fallbackCopy(text);
        showFeedback();
    }
}
// Inject the copy button between patient name and sex inside #patient-label.
// Runs on DOMContentLoaded because this script block is in the <head> before the elements exist.
document.addEventListener('DOMContentLoaded', function() {
(function() {
    var label = document.getElementById('patient-label');
    if (!label) return;
    var sex = document.getElementById('patient-sex');
    var pronouns = document.getElementById('patient-pronouns');
    // Insert before sex (or pronouns if present, since pronouns comes before sex)
    var insertBefore = pronouns || sex;
    if (!insertBefore) return;

    var btn = document.createElement('div');
    btn.id = 'copy-demo-btn';
    btn.title = 'Copy patient info to clipboard';
    btn.style.cssText = 'cursor:pointer;display:inline-flex;align-items:center;opacity:0.5;';
    btn.onmouseover = function() { this.style.opacity = '1'; };
    btn.onmouseout = function() { this.style.opacity = '0.5'; };
    btn.onclick = function() { copyDemoToClipboard(this); };

    // Build SVG copy icon (two overlapping rectangles) using DOM API
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '14');
    svg.setAttribute('height', '14');
    svg.setAttribute('viewBox', '0 0 16 16');
    svg.setAttribute('fill', 'currentColor');
    var back = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    back.setAttribute('x', '5'); back.setAttribute('y', '0');
    back.setAttribute('width', '10'); back.setAttribute('height', '13');
    back.setAttribute('rx', '1.5'); back.setAttribute('ry', '1.5');
    back.setAttribute('stroke', 'currentColor'); back.setAttribute('stroke-width', '1.2');
    back.setAttribute('fill', 'none');
    var front = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    front.setAttribute('x', '1'); front.setAttribute('y', '3');
    front.setAttribute('width', '10'); front.setAttribute('height', '13');
    front.setAttribute('rx', '1.5'); front.setAttribute('ry', '1.5');
    front.setAttribute('stroke', 'currentColor'); front.setAttribute('stroke-width', '1.2');
    front.setAttribute('fill', 'white');
    svg.appendChild(back);
    svg.appendChild(front);
    btn.appendChild(svg);

    label.insertBefore(btn, insertBefore);
})();
});

/**
 * Copies patient demographic, provider, and clinic information to the clipboard
 * as structured JSON. Intended for pasting into external referral forms, fax cover
 * sheets, or other systems that need patient context.
 *
 * Data sources: Demographic model (patient), LoggedInInfo (provider), ClinicDAO (clinic).
 * All values are OWASP-encoded server-side via Encode.forJavaScript().
 *
 * @param {HTMLElement} el - The button element, used for visual feedback
 */
function copyDemoToClipboard(el) {
    var data = {
        patient: {
            lastName:    "<%= Encode.forJavaScript(demographic.getLastName() != null ? demographic.getLastName() : "") %>",
            firstName:   "<%= Encode.forJavaScript(demographic.getFirstName() != null ? demographic.getFirstName() : "") %>",
            dateOfBirth: "<%= Encode.forJavaScript(demographic.getBirthDayAsString() != null ? demographic.getBirthDayAsString() : "") %>",
            age:         "<%= Encode.forJavaScript(demographic.getAge() != null ? demographic.getAge() : "") %>",
            sex:         "<%= Encode.forJavaScript(demographic.getSex() != null ? demographic.getSex() : "") %>",
            hin:         "<%= Encode.forJavaScript(demographic.getHin() != null ? demographic.getHin() : "") %>",
            hinVer:      "<%= Encode.forJavaScript(demographic.getVer() != null ? demographic.getVer() : "") %>",
            phone:       "<%= Encode.forJavaScript(demographic.getPhone() != null ? demographic.getPhone() : "") %>",
            phoneWork:   "<%= Encode.forJavaScript(demographic.getPhone2() != null ? demographic.getPhone2() : "") %>",
            phoneCell:   "<%= Encode.forJavaScript(demographic.getCellPhone() != null ? demographic.getCellPhone() : "") %>",
            email:       "<%= Encode.forJavaScript(demographic.getEmail() != null ? demographic.getEmail() : "") %>",
            address:     "<%= Encode.forJavaScript(demographic.getAddress() != null ? demographic.getAddress() : "") %>",
            city:        "<%= Encode.forJavaScript(demographic.getCity() != null ? demographic.getCity() : "") %>",
            province:    "<%= Encode.forJavaScript(demographic.getProvince() != null ? demographic.getProvince() : "") %>",
            postalCode:  "<%= Encode.forJavaScript(demographic.getPostal() != null ? demographic.getPostal() : "") %>",
            mrp:         "<%= mrp != null ? Encode.forJavaScript(mrp.getFormattedName()) : "" %>",
            rosterStatus:"<%= Encode.forJavaScript(demographic.getRosterStatus() != null ? demographic.getRosterStatus() : "") %>"
        },
        provider: {
            name:   "<%= Encode.forJavaScript(loggedInProvider.getFormattedName() != null ? loggedInProvider.getFormattedName() : "") %>",
            cpso:   "<%= Encode.forJavaScript(loggedInProvider.getPractitionerNo() != null ? loggedInProvider.getPractitionerNo() : "") %>",
            ohipNo: "<%= Encode.forJavaScript(loggedInProvider.getOhipNo() != null ? loggedInProvider.getOhipNo() : "") %>"
        },
        clinic: {
            name:       "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicName()) : "" %>",
            address:    "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicAddress()) : "" %>",
            city:       "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicCity()) : "" %>",
            province:   "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicProvince()) : "" %>",
            postalCode: "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicPostal()) : "" %>",
            phone:      "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicPhone()) : "" %>",
            fax:        "<%= clinic != null ? Encode.forJavaScript(clinic.getClinicFax()) : "" %>"
        }
    };
    copyToClip(JSON.stringify(data, null, 2), el);
}

/**
 * Fallback clipboard copy for browsers that don't support the Clipboard API.
 * Creates an off-screen textarea, selects its content, and uses the deprecated
 * execCommand('copy') to write to the clipboard.
 *
 * @param {string} text - The text to copy to the clipboard
 */
function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
}
</script>

<div id="header-top-row">
    <div id="left-column">
        <div id="branding-logo">
            <img alt="CARLOS EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19px">
        </div>
        <%= demographic.getStandardIdentificationHTML(request.getContextPath()) %>
        <%-- Copy-to-clipboard button is injected between #patient-full-name and #patient-sex via JS above --%>
    </div>
    <div id="right-column">
    </div>
</div>

<div id="header-bottom-row">
    <% if (CarlosProperties.getInstance().hasProperty("ONTARIO_MD_INCOMINGREQUESTOR")) {%>
    <div>
        <a href="javascript:void(0);" onClick="popupPage(600,175,'Calculators','<c:out
                value="${ctx}"/>/commons/omdDiseaseList.jsp?sex=<%=bean.patientSex%>&age=<%=demographic.getAge()%>'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.Header.OntMD"/></a>
    </div>
    <%}%>

    <div>
        <%=getEChartLinks() %>
    </div>

</div>

<%!
    String getEChartLinks() {
        String str = CarlosProperties.getInstance().getProperty("ECHART_LINK");
        if (str == null) {
            return "";
        }
        try {
            String[] httpLink = str.split("\\|");
            return "<a target=\"_blank\" href=\"" + httpLink[1] + "\">" + httpLink[0] + "</a>";
        } catch (Exception e) {
            MiscUtils.getLogger().error("ECHART_LINK is not in the correct format. title|url :" + str, e);
        }
        return "";
    }
%>
